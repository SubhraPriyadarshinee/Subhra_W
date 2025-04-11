package com.walmart.move.nim.receiving.rdc.utils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.testng.AssertJUnit.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.hawkeye.HawkeyeRestApiClient;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.HawkeyeGetLpnsRequest;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.HawkeyeItemUpdateRequest;
import com.walmart.move.nim.receiving.core.client.nimrds.model.Destination;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaLabelDataPublisher;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadContainerDTO;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadDistributionsDTO;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadItemDTO;
import com.walmart.move.nim.receiving.core.model.instruction.LabelDataAllocationDTO;
import com.walmart.move.nim.receiving.core.model.label.acl.LabelType;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.model.symbotic.RdcVerificationMessage;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.rdc.mock.data.MockRdcInstruction;
import com.walmart.move.nim.receiving.rdc.model.LabelInstructionStatus;
import com.walmart.move.nim.receiving.rdc.model.wft.WFTInstruction;
import com.walmart.move.nim.receiving.rdc.service.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.IOException;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcAutoReceivingUtilsTest {

  @InjectMocks private RdcAutoReceivingUtils rdcAutoReceivingUtils;
  @Mock private RdcDeliveryService rdcDeliveryService;
  @Mock private RdcInstructionUtils rdcInstructionUtils;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private RdcContainerUtils rdcContainerUtils;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private NimRdsService nimRdsService;
  @Mock private ContainerService containerService;
  @Mock private RdcReceivingUtils rdcReceivingUtils;
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private LabelDataService labelDataService;
  @Mock private KafkaLabelDataPublisher labelDataPublisher;
  @Mock private LabelDownloadEventService labelDownloadEventService;
  @Mock private HawkeyeRestApiClient hawkeyeRestApiClient;
  @Mock private RdcDeliveryMetaDataService rdcDeliveryMetaDataService;
  @Mock AppConfig appConfig;

  private HttpHeaders httpHeaders;
  private String facilityNum = "32818";
  private String facilityCountryCode = "us";
  private Gson gson;
  private final List<String> VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS =
      Arrays.asList("CC", "CI", "CJ");

  @BeforeClass
  public void initMocks() {
    gson = new Gson();
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(rdcAutoReceivingUtils, "gson", gson);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    TenantContext.setFacilityCountryCode(facilityCountryCode);
    TenantContext.setCorrelationId("2323-323dsds-323dwsd-3d23e");
  }

  @BeforeMethod
  public void setup() {
    httpHeaders = MockHttpHeaders.getHeaders(facilityNum, facilityCountryCode);
    httpHeaders.add(RdcConstants.WFT_LOCATION_ID, "23");
    httpHeaders.add(RdcConstants.WFT_LOCATION_TYPE, "DOOR-23");
    httpHeaders.add(RdcConstants.WFT_SCC_CODE, "0086623");
  }

  @AfterMethod
  public void tearDown() {
    reset(
        rdcDeliveryService,
        rdcInstructionUtils,
        tenantSpecificConfigReader,
        rdcContainerUtils,
        containerPersisterService,
        nimRdsService,
        containerService,
        rdcReceivingUtils,
        labelDataService,
        hawkeyeRestApiClient,
        instructionPersisterService,
        labelDataPublisher,
        labelDownloadEventService,
        appConfig);
  }

  @Test
  public void testUpdateCatalogInHawkeye_ItemPresentInLabelData() {
    doReturn(Collections.singletonList(getMockLabelData()))
        .when(labelDataService)
        .findByItemNumber(anyLong());
    doNothing()
        .when(hawkeyeRestApiClient)
        .sendItemUpdateToHawkeye(getHawkeyeItemUpdateRequest(), MockHttpHeaders.getHeaders());
    rdcAutoReceivingUtils.updateCatalogInHawkeye(getItemCatalogUpdateRequest(), httpHeaders);
    verify(labelDataService, times(1)).findByItemNumber(anyLong());
    verify(hawkeyeRestApiClient, times(1))
        .sendItemUpdateToHawkeye(any(HawkeyeItemUpdateRequest.class), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testFetchLabelDataAutomation_CancelledLabelStatus()
      throws ReceivingException, IOException {
    AutoReceiveRequest autoReceiveRequest = getAutoReceiveRequestForDA();
    autoReceiveRequest.setMessageId(null);
    ContainerItem containerItem = new ContainerItem();
    containerItem.setItemNumber(1232323L);
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0).setPackType("C");
    LabelData labelData = getLabelDataForDA().get(0);
    labelData.setStatus(LabelInstructionStatus.CANCELLED.name());
    when(labelDataService.findByTrackingId(anyString())).thenReturn(labelData);
    LabelData labelData1 =
        rdcAutoReceivingUtils.fetchLabelData(deliveryDocumentList, autoReceiveRequest, false);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testFetchLabelDataAutomation_NoLabelData() throws ReceivingException, IOException {
    AutoReceiveRequest autoReceiveRequest = getAutoReceiveRequestForDA();
    autoReceiveRequest.setMessageId(null);
    ContainerItem containerItem = new ContainerItem();
    containerItem.setItemNumber(1232323L);
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0).setPackType("C");
    when(labelDataService.findByTrackingId(anyString())).thenReturn(null);
    rdcAutoReceivingUtils.fetchLabelData(deliveryDocumentList, autoReceiveRequest, false);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testFetchLabelDataExceptionReceiving_NoLabelData()
      throws ReceivingException, IOException {
    AutoReceiveRequest autoReceiveRequest = getAutoReceiveRequestForDA();
    autoReceiveRequest.setMessageId(null);
    autoReceiveRequest.setFeatureType(RdcConstants.EXCEPTION_HANDLING);
    ContainerItem containerItem = new ContainerItem();
    containerItem.setItemNumber(1232323L);
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BI");
    autoReceiveRequest.setDeliveryDocuments(deliveryDocumentList);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            anyString(), anyLong(), anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(new ArrayList<>());
    rdcAutoReceivingUtils.fetchLabelData(deliveryDocumentList, autoReceiveRequest, true);
  }

  @Test
  public void testBuildContainerItemAndContainerForDA() throws IOException {
    ContainerItem containerItem = new ContainerItem();
    containerItem.setItemNumber(1232323L);
    when(rdcContainerUtils.buildContainer(
            anyString(),
            any(Long.class),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class)))
        .thenReturn(MockRdcInstruction.getContainer());
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            eq(null),
            eq(null),
            anyList(),
            eq(null)))
        .thenReturn(containerItem);
    when(containerPersisterService.saveContainer(any(Container.class)))
        .thenReturn(MockRdcInstruction.getContainer());
    rdcAutoReceivingUtils.buildContainerItemAndContainerForDA(
        getAutoReceiveRequestForDA(),
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0),
        "12345677",
        "sysadmin",
        buildMockReceivedContainer(),
        123L,
        InventoryStatus.AVAILABLE.name());
    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            eq(null),
            eq(null),
            anyList(),
            eq(null));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            anyString(),
            any(Long.class),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
  }

  @Test
  public void testGetReceivedContainerInfo() {
    ReceivedContainer receivedContainer =
        rdcAutoReceivingUtils.getReceivedContainerInfo("123456", "A0002");
    assertNotNull(receivedContainer);
    assertEquals(receivedContainer.getLabelTrackingId(), "123456");
    assertEquals(receivedContainer.getDestinations().get(0).getSlot(), "A0002");
  }

  @Test
  public void buildReceiveInstructionRequest() throws IOException {
    AutoReceiveRequest autoReceiveRequest = getAutoReceiveRequestForDA();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    ReceiveInstructionRequest receiveInstructionRequest =
        rdcAutoReceivingUtils.buildReceiveInstructionRequest(autoReceiveRequest, deliveryDocument);
    assertNotNull(receiveInstructionRequest);
    assertEquals(autoReceiveRequest.getDoorNumber(), receiveInstructionRequest.getDoorNumber());
    assertEquals(
        deliveryDocument.getDeliveryDocumentLines(),
        receiveInstructionRequest.getDeliveryDocumentLines());
  }

  @Test
  public void testFetchLabelData_ExceptionReceiving() throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CI");
    AutoReceiveRequest autoReceiveRequest = getAutoReceiveRequestForDA();
    autoReceiveRequest.setLpn(null);
    doReturn(VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS)
        .when(appConfig)
        .getValidItemPackTypeHandlingCodeCombinations();
    when(hawkeyeRestApiClient.getLpnsFromHawkeye(
            any(HawkeyeGetLpnsRequest.class), any(HttpHeaders.class)))
        .thenReturn(Optional.of(Arrays.asList("a602042323232323")));
    when(labelDataService.findByTrackingId(anyString())).thenReturn(getLabelDataForDA().get(0));
    LabelData labelData =
        rdcAutoReceivingUtils.fetchLabelData(
            deliveryDocumentList, autoReceiveRequest, Boolean.TRUE);
    assertNotNull(labelData);
    verify(hawkeyeRestApiClient, times(1))
        .getLpnsFromHawkeye(any(HawkeyeGetLpnsRequest.class), any(HttpHeaders.class));
    verify(containerService, times(1)).findByTrackingId(anyString());
    verify(labelDataService, times(1)).findByTrackingId(anyString());
  }

  @Test
  public void testFetchLabelData_ExceptionReceiving_HawkeyeReturnsEmptyLpnList()
      throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CI");
    AutoReceiveRequest autoReceiveRequest = getAutoReceiveRequestForDA();
    autoReceiveRequest.setLpn(null);
    doReturn(VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS)
        .when(appConfig)
        .getValidItemPackTypeHandlingCodeCombinations();
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            anyString(), anyLong(), anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(getLabelDataForDA());
    LabelData labelData =
        rdcAutoReceivingUtils.fetchLabelData(
            deliveryDocumentList, autoReceiveRequest, Boolean.TRUE);
    assertNotNull(labelData);
    verify(hawkeyeRestApiClient, times(1))
        .getLpnsFromHawkeye(any(HawkeyeGetLpnsRequest.class), any(HttpHeaders.class));
    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            anyString(), anyLong(), anyString(), anyInt(), anyInt(), anyString());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testFetchLabelData_ExceptionReceiving_HawkeyeReturnsEmptyLpnList_NoLabelData()
      throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CI");
    AutoReceiveRequest autoReceiveRequest = getAutoReceiveRequestForDA();
    autoReceiveRequest.setLpn(null);
    doReturn(VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS)
        .when(appConfig)
        .getValidItemPackTypeHandlingCodeCombinations();
    rdcAutoReceivingUtils.fetchLabelData(deliveryDocumentList, autoReceiveRequest, Boolean.TRUE);
  }

  @Test
  public void testBuildContainerAndContainerItemsForSSTK() throws IOException {
    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument = MockDeliveryDocuments.getDeliveryDocumentsForSSTK().get(0);
    AutoReceiveRequest autoReceiveRequest = getAutoReceiveRequestForSSTK();
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), eq(null)))
        .thenReturn(getMockContainerItem());
    when(rdcContainerUtils.buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            eq(null)))
        .thenReturn(getMockContainer());
    rdcAutoReceivingUtils.buildContainerAndContainerItemForSSTK(
        instruction, deliveryDocument, autoReceiveRequest, "sysadmin", "a602042323232323", "A0002");
    verify(rdcContainerUtils, times(1))
        .buildContainerItem(anyString(), any(DeliveryDocument.class), anyInt(), eq(null));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            eq(null));
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
  }

  @Test
  public void testBuildReceivedContainerForSSTK() throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    ReceivedContainer receivedContainer =
        rdcAutoReceivingUtils.buildReceivedContainerForSSTK(
            "a602042323232323", deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0));
    assertNotNull(receivedContainer);
    assertEquals(receivedContainer.getLabelTrackingId(), "a602042323232323");
    assertEquals(
        receivedContainer.getDestinations().get(0).getSlot(),
        deliveryDocumentList
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getPrimeSlot());
  }

  @Test
  public void testCreateInstructionForDaAndSstk() throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    AutoReceiveRequest autoReceiveRequest = getAutoReceiveRequestForSSTK();
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(
            i -> {
              Instruction instruction = (Instruction) i.getArguments()[0];
              return instruction;
            });
    Instruction instruction =
        rdcAutoReceivingUtils.createInstruction(
            autoReceiveRequest, deliveryDocumentList.get(0), MockHttpHeaders.getHeaders());
    assertNotNull(instruction);
    assertEquals(instruction.getActivityName(), WFTInstruction.ACL.getActivityName());
    assertEquals(instruction.getInstructionMsg(), WFTInstruction.ACL.getMessage());
  }

  @Test
  public void testUpdateInstructionForDA() {
    Instruction instruction = MockRdcInstruction.getInstruction();
    doCallRealMethod()
        .when(rdcContainerUtils)
        .getContainerDetails(anyString(), anyMap(), any(ContainerType.class), anyString());
    rdcAutoReceivingUtils.updateInstruction(
        instruction,
        buildMockReceivedContainer(),
        1,
        getPrintLabelData(),
        "sysadmin",
        Boolean.TRUE);
    assertNotNull(instruction);
    assertEquals(
        instruction.getContainer().getOutboundChannelMethod(),
        RdcConstants.OUTBOUND_CHANNEL_METHOD_CROSSDOCK);
  }

  @Test
  public void testUpdateInstructionForSSTK() throws IOException {
    Instruction instruction = MockRdcInstruction.getInstruction();
    doCallRealMethod()
        .when(rdcContainerUtils)
        .getContainerDetails(anyString(), anyMap(), any(ContainerType.class), anyString());
    rdcAutoReceivingUtils.updateInstruction(
        instruction,
        buildMockReceivedContainer(),
        1,
        getPrintLabelData(),
        "sysadmin",
        Boolean.FALSE);
    assertNotNull(instruction);
    assertEquals(
        instruction.getContainer().getOutboundChannelMethod(),
        RdcConstants.OUTBOUND_CHANNEL_METHOD_SSTKU);
  }

  @Test
  public void testTransformLabelData() throws IOException {
    List<ReceivedContainer> receivedContainers =
        rdcAutoReceivingUtils.transformLabelData(
            getLabelDataForDA(), MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0));
    assertNotNull(receivedContainers);
  }

  @Test
  public void testValidateProDate() throws IOException {
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    rdcAutoReceivingUtils.validateProDate(deliveryDocument);
    assertNotNull(deliveryDocument.getProDate());
  }

  @Test
  public void testSetLocationHeaders() {
    Optional<DeliveryMetaData> deliveryMetaData = Optional.of(new DeliveryMetaData());
    deliveryMetaData.get().setDoorNumber("100");
    doReturn(deliveryMetaData).when(rdcDeliveryMetaDataService).findByDeliveryNumber(anyString());
    rdcAutoReceivingUtils.setLocationHeaders(getAutoReceiveRequest(), MockHttpHeaders.getHeaders());
    verify(rdcDeliveryMetaDataService, times(1)).findByDeliveryNumber(anyString());
  }

  @Test
  public void testBuildLabelType() throws IOException {
    // TODO check on valid pack type and handling code
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BC");
    doReturn(VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS)
        .when(appConfig)
        .getValidItemPackTypeHandlingCodeCombinations();
    AutoReceiveRequest autoReceiveRequest = getAutoReceiveRequest();
    autoReceiveRequest.setFeatureType(RdcConstants.EXCEPTION_HANDLING);
    rdcAutoReceivingUtils.buildLabelType(
        buildMockReceivedContainer(), autoReceiveRequest, deliveryDocumentList.get(0));
    assertFalse(autoReceiveRequest.isFlibEligible());
  }

  @Test
  public void testGetGdmDeliveryDocuments() throws IOException, ReceivingException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    when(rdcDeliveryService.getDeliveryDocumentsByPoAndPoLineFromGDM(
            anyString(), anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    rdcAutoReceivingUtils.getGdmDeliveryDocuments(
        getAutoReceiveRequest(), MockHttpHeaders.getHeaders());
    verify(rdcDeliveryService, times(1))
        .getDeliveryDocumentsByPoAndPoLineFromGDM(
            anyString(), anyString(), anyInt(), any(HttpHeaders.class));
  }

  @Test
  public void testGetGdmDeliveryDocuments_EmptyAdditionalInfo()
      throws IOException, ReceivingException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0).setAdditionalInfo(null);
    when(rdcDeliveryService.getDeliveryDocumentsByPoAndPoLineFromGDM(
            anyString(), anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    rdcAutoReceivingUtils.getGdmDeliveryDocuments(
        getAutoReceiveRequest(), MockHttpHeaders.getHeaders());
    verify(rdcDeliveryService, times(1))
        .getDeliveryDocumentsByPoAndPoLineFromGDM(
            anyString(), anyString(), anyInt(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1)).updateAdditionalItemDetailsFromGDM(anyList());
  }

  @Test
  public void testGetGdmDeliveryDocuments_IqsDisabled() throws IOException, ReceivingException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0).setAdditionalInfo(null);
    when(rdcDeliveryService.getDeliveryDocumentsByPoAndPoLineFromGDM(
            anyString(), anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    rdcAutoReceivingUtils.getGdmDeliveryDocuments(
        getAutoReceiveRequest(), MockHttpHeaders.getHeaders());
    verify(rdcDeliveryService, times(1))
        .getDeliveryDocumentsByPoAndPoLineFromGDM(
            anyString(), anyString(), anyInt(), any(HttpHeaders.class));
    verify(nimRdsService, times(1)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
  }

  @Test
  public void testValidateDeliveryDocuments() throws IOException, ReceivingException {
    rdcAutoReceivingUtils.validateDeliveryDocuments(
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA(),
        getAutoReceiveRequest(),
        MockHttpHeaders.getHeaders());
    verify(rdcInstructionUtils, times(1))
        .validateAndProcessGdmDeliveryDocuments(anyList(), any(InstructionRequest.class));
    verify(rdcInstructionUtils, times(1))
        .validatePoLineIsCancelledOrClosedOrRejected(any(DeliveryDocumentLine.class));
    verify(rdcReceivingUtils, times(1))
        .validateOverage(anyList(), anyInt(), any(HttpHeaders.class), anyBoolean());
  }

  @Test
  public void testBuildAutoReceiveRequest() {
    RdcVerificationMessage rdcVerificationMessage = getRdcVerificationMessage();
    AutoReceiveRequest autoReceiveRequest =
        rdcAutoReceivingUtils.buildAutoReceiveRequest(rdcVerificationMessage);
    assertNotNull(autoReceiveRequest);
    assertEquals(rdcVerificationMessage.getLpn(), autoReceiveRequest.getLpn());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testAutoReceiveService_isLpnAlreadyReceived() throws ReceivingException {
    when(containerService.findByTrackingId(anyString())).thenReturn(new Container());
    rdcAutoReceivingUtils.isLpnAlreadyReceived("a602042323232323");
  }

  @Test
  public void testPersistLabelInfo_ReceivingBadDataException() throws ReceivingBadDataException {
    doThrow(ReceivingBadDataException.class)
        .when(labelDownloadEventService)
        .findByDeliveryNumberAndPurchaseReferenceNumberAndItemNumber(
            anyLong(), anyString(), anyLong());
    verify(labelDownloadEventService, times(0)).saveAll(any());
  }

  private AutoReceiveRequest getAutoReceiveRequestForSSTK() {
    AutoReceiveRequest autoReceiveRequest = new AutoReceiveRequest();
    autoReceiveRequest.setLpn("a602042323232323");
    autoReceiveRequest.setDoorNumber("423");
    autoReceiveRequest.setDeliveryNumber(232323323L);
    autoReceiveRequest.setQuantity(1);
    autoReceiveRequest.setQuantityUOM("ZA");
    autoReceiveRequest.setPurchaseReferenceNumber("5232232323");
    autoReceiveRequest.setPurchaseReferenceLineNumber(1);
    autoReceiveRequest.setMessageId("35345-4432323");
    return autoReceiveRequest;
  }

  private AutoReceiveRequest getAutoReceiveRequest() {
    AutoReceiveRequest autoReceiveRequest = new AutoReceiveRequest();
    autoReceiveRequest.setLpn("a602042323232323");
    autoReceiveRequest.setDoorNumber("423");
    autoReceiveRequest.setDeliveryNumber(232323323L);
    autoReceiveRequest.setQuantity(1);
    autoReceiveRequest.setQuantityUOM("ZA");
    autoReceiveRequest.setPurchaseReferenceNumber("5232232323");
    autoReceiveRequest.setPurchaseReferenceLineNumber(1);
    autoReceiveRequest.setMessageId("35345-4432323");
    return autoReceiveRequest;
  }

  private AutoReceiveRequest getAutoReceiveRequestForDA() {
    AutoReceiveRequest autoReceiveRequest = new AutoReceiveRequest();
    autoReceiveRequest.setLpn("a602042323232323");
    autoReceiveRequest.setDoorNumber("423");
    autoReceiveRequest.setDeliveryNumber(232323323L);
    autoReceiveRequest.setQuantity(1);
    autoReceiveRequest.setQuantityUOM("ZA");
    autoReceiveRequest.setPurchaseReferenceNumber("5232232323");
    autoReceiveRequest.setPurchaseReferenceLineNumber(1);
    autoReceiveRequest.setMessageId("35345-4432323");
    return autoReceiveRequest;
  }

  private List<LabelData> getLabelDataForDA() {
    LabelData labelData = new LabelData();
    labelData.setPurchaseReferenceNumber("5232232323");
    labelData.setPurchaseReferenceLineNumber(1);
    labelData.setDeliveryNumber(232323323L);
    labelData.setLpns("a602042323232323");
    LabelDataAllocationDTO allocation = getMockLabelDataAllocationDTO();
    labelData.setAllocation(allocation);
    labelData.setStatus("AVAILABLE");
    return Collections.singletonList(labelData);
  }

  private LabelDataAllocationDTO getMockLabelDataAllocationDTO() {
    LabelDataAllocationDTO allocation = new LabelDataAllocationDTO();
    InstructionDownloadContainerDTO container = new InstructionDownloadContainerDTO();
    container.setTrackingId("a602042323232323");
    container.setCtrType("CASE");
    container.setOutboundChannelMethod("DA");
    InstructionDownloadDistributionsDTO distributions = new InstructionDownloadDistributionsDTO();
    InstructionDownloadItemDTO item = new InstructionDownloadItemDTO();
    item.setItemNbr(658790758L);
    item.setAisle("12");
    item.setItemUpc("78236478623");
    item.setPickBatch("281");
    item.setPrintBatch("281");
    item.setZone("03");
    item.setVnpk(1);
    item.setWhpk(1);
    distributions.setItem(item);
    distributions.setOrderId("1234");
    distributions.setQtyUom("ZA");
    distributions.setAllocQty(1);
    container.setDistributions(Collections.singletonList(distributions));
    Facility finalDestination = new Facility();
    finalDestination.setBuNumber("87623");
    finalDestination.setCountryCode("US");
    container.setFinalDestination(finalDestination);
    allocation.setContainer(container);
    return allocation;
  }

  private static LabelData getMockLabelData() {
    return LabelData.builder()
        .deliveryNumber(94769060L)
        .purchaseReferenceNumber("3615852071")
        .isDAConveyable(Boolean.TRUE)
        .itemNumber(566051127L)
        .lpnsCount(6)
        .labelSequenceNbr(20231016000100001L)
        .labelType(LabelType.ORDERED)
        .build();
  }

  private static ItemCatalogUpdateRequest getItemCatalogUpdateRequest() {
    ItemCatalogUpdateRequest itemCatalogUpdateRequest = new ItemCatalogUpdateRequest();
    itemCatalogUpdateRequest.setDeliveryNumber("87654321");
    itemCatalogUpdateRequest.setItemNumber(567898765L);
    itemCatalogUpdateRequest.setNewItemUPC("20000943037194");
    return itemCatalogUpdateRequest;
  }

  private HawkeyeItemUpdateRequest getHawkeyeItemUpdateRequest() {
    return HawkeyeItemUpdateRequest.builder()
        .itemNumber("123456")
        .catalogGTIN("01234567891234")
        .build();
  }

  private ReceivedContainer buildMockReceivedContainer() {
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId("1234567");
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("A0002");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    receivedContainer.setPack(RdcConstants.QTY_TO_RECEIVE);
    receivedContainer.setDistributions(new ArrayList<>());
    return receivedContainer;
  }

  private Container getMockContainer() {
    Container container = new Container();
    container.setTrackingId("MOCK_TRACKING_ID");
    container.setInstructionId(123L);
    container.setParentTrackingId(null);
    container.setContainerItems(getMockContainerItem());
    container.setCreateUser("sysadmin");
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.SLOT, "A0001");
    container.setDestination(destination);
    return container;
  }

  private List<ContainerItem> getMockContainerItem() {
    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId("MOCK_TRACKING_ID");
    containerItem.setPurchaseReferenceNumber("PO123");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(20);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setItemNumber(123456L);
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM, "true");
    containerItem.setContainerItemMiscInfo(containerItemMiscInfo);
    return Collections.singletonList(containerItem);
  }

  private Map<String, Object> getPrintLabelData() {
    List<PrintLabelRequest> printLabelRequests = new ArrayList<>();
    PrintLabelRequest printLabelRequest = new PrintLabelRequest();
    printLabelRequest.setFormatName(ReceivingConstants.PRINT_LABEL_FORMAT_NAME);
    printLabelRequest.setTtlInHours(ReceivingConstants.PRINT_LABEL_DEFAULT_TTL);
    printLabelRequest.setLabelIdentifier("q0602000000000234234234");
    printLabelRequests.add(printLabelRequest);

    Map<String, Object> printLabelData = new HashMap<>();
    printLabelData.put(ReceivingConstants.PRINT_HEADERS_KEY, "Headers");
    printLabelData.put(ReceivingConstants.PRINT_CLIENT_ID_KEY, "Atlas-RCV");
    printLabelData.put(ReceivingConstants.PRINT_REQUEST_KEY, printLabelRequests);
    return printLabelData;
  }

  private RdcVerificationMessage getRdcVerificationMessage() {
    RdcVerificationMessage rdcVerificationMessage = new RdcVerificationMessage();
    rdcVerificationMessage.setLpn("a602042323232323");
    rdcVerificationMessage.setMessageType("NORMAL");
    rdcVerificationMessage.setDeliveryNumber("234234234");
    return rdcVerificationMessage;
  }
}
