package com.walmart.move.nim.receiving.rdc.utils;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_REINDUCT_ROUTING_LABEL;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.core.client.nimrds.AsyncNimRdsRestApiClient;
import com.walmart.move.nim.receiving.core.client.nimrds.NimRDSRestApiClient;
import com.walmart.move.nim.receiving.core.client.nimrds.model.*;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.OutboxConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.message.publisher.JMSSorterPublisher;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaAthenaPublisher;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.ei.InventoryDetails;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.label.acl.ACLLabelDataTO;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponseWithRdsResponse;
import com.walmart.move.nim.receiving.core.model.sorter.LabelType;
import com.walmart.move.nim.receiving.core.model.symbotic.SymFreightType;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.repositories.ProblemRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.core.transformer.InventoryTransformer;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.label.LabelFormat;
import com.walmart.move.nim.receiving.rdc.mock.data.*;
import com.walmart.move.nim.receiving.rdc.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.rdc.mock.data.MockInstructionRequest;
import com.walmart.move.nim.receiving.rdc.mock.data.MockRdcInstruction;
import com.walmart.move.nim.receiving.rdc.mock.data.MockRdsResponse;
import com.walmart.move.nim.receiving.rdc.model.LabelAction;
import com.walmart.move.nim.receiving.rdc.model.wft.RdcInstructionType;
import com.walmart.move.nim.receiving.rdc.model.wft.WFTInstruction;
import com.walmart.move.nim.receiving.rdc.service.NimRdsService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.PurchaseReferenceType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.platform.messages.MetaData;
import com.walmart.platform.repositories.OutboxEvent;
import com.walmart.platform.repositories.PayloadRef;
import com.walmart.platform.service.OutboxEventSinkService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcReceivingUtilsTest {

  @InjectMocks RdcReceivingUtils rdcReceivingUtils;

  @Mock private RdcInstructionUtils rdcInstructionUtils;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private ReceiptService receiptService;
  @Mock private DeliveryDocumentHelper deliveryDocumentHelper;
  @Mock private NimRdsService nimRdsService;
  @Mock private AsyncNimRdsRestApiClient asyncNimRdsRestApiClient;
  @Mock private NimRDSRestApiClient nimRDSRestApiClient;
  @Mock private DeliveryItemOverrideService deliveryItemOverrideService;
  @Mock private ProblemRepository problemRepository;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private RdcContainerUtils rdcContainerUtils;
  @Mock private KafkaAthenaPublisher kafkaAthenaPublisher;
  @Mock private JMSSorterPublisher jmsSorterPublisher;
  @Mock private RdcManagedConfig rdcManagedConfig;
  @Mock private AppConfig appConfig;
  @Mock private SymboticPutawayPublishHelper symboticPutawayPublishHelper;
  @Mock private RdcSlottingUtils rdcSlottingUtils;
  @Mock private InventoryTransformer inventoryTransformer;
  @Mock private OutboxEventSinkService outboxEventSinkService;
  @Mock private ContainerItemRepository containerItemRepository;
  @Mock private ContainerRepository containerRepository;
  @Mock private InstructionHelperService instructionHelperService;
  @Mock private LabelDataService labelDataService;
  @Mock private EIService eiService;
  @Mock private LocationService locationService;
  @Mock private OutboxConfig outboxConfig;

  private ContainerTransformer containerTransformer = mock(ContainerTransformer.class);
  private Gson gson;
  private static final String facilityNum = "32818";
  private static final String countryCode = "US";
  private HttpHeaders headers;
  private final List<String> VALID_ASRS_LIST =
      Arrays.asList(
          ReceivingConstants.SYM_BRKPK_ASRS_VALUE, ReceivingConstants.SYM_CASE_PACK_ASRS_VALUE);

  @BeforeMethod
  public void setup() {
    gson = new Gson();
    headers = MockHttpHeaders.getHeaders(facilityNum, countryCode);
    headers.add(RdcConstants.WFT_LOCATION_ID, "23");
    headers.add(RdcConstants.WFT_LOCATION_TYPE, "DOOR-23");
    headers.add(RdcConstants.WFT_SCC_CODE, "0086623");
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setCorrelationId("2323-323dsds-323dwsd-3d23e");
    ReflectionTestUtils.setField(rdcReceivingUtils, "gson", gson);
  }

  @AfterMethod
  public void tearDown() {
    reset(
        rdcInstructionUtils,
        containerPersisterService,
        instructionPersisterService,
        receiptService,
        deliveryDocumentHelper,
        tenantSpecificConfigReader,
        deliveryItemOverrideService,
        nimRdsService,
        asyncNimRdsRestApiClient,
        nimRDSRestApiClient,
        problemRepository,
        kafkaAthenaPublisher,
        jmsSorterPublisher,
        problemRepository,
        rdcManagedConfig,
        rdcContainerUtils,
        containerTransformer,
        symboticPutawayPublishHelper,
        instructionHelperService,
        inventoryTransformer,
        labelDataService,
        eiService,
        locationService);
  }

  @Test
  public void testIsPoAndPoLineInReceivableStatus_HappyPath()
      throws IOException, ReceivingException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    doNothing()
        .when(rdcInstructionUtils)
        .validatePoLineIsCancelledOrClosedOrRejected(any(DeliveryDocumentLine.class));
    doNothing().when(rdcInstructionUtils).validateItemXBlocked(any(DeliveryDocumentLine.class));
    doNothing()
        .when(rdcInstructionUtils)
        .validateItemHandlingMethod(any(DeliveryDocumentLine.class));

    rdcReceivingUtils.isPoAndPoLineInReceivableStatus(
        deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testIsPoAndPoLineInReceivableStatus_ThrowsException()
      throws IOException, ReceivingException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0);
    doNothing()
        .when(rdcInstructionUtils)
        .validatePoLineIsCancelledOrClosedOrRejected(any(DeliveryDocumentLine.class));
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.CREATE_INSTRUCTION_ERROR,
                String.format(
                    RdcConstants.X_BLOCK_ITEM_ERROR_MSG, deliveryDocumentLine.getItemNbr()),
                String.valueOf(deliveryDocumentLine.getItemNbr())))
        .when(rdcInstructionUtils)
        .validateItemXBlocked(any(DeliveryDocumentLine.class));
    rdcReceivingUtils.isPoAndPoLineInReceivableStatus(
        deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0));
  }

  @Test
  public void testPersistReceivedContainerDetails() {
    Instruction instruction = MockRdcInstruction.getInstruction();
    Container container = getMockContainer();
    ContainerItem containerItem = getMockContainerItem();
    Receipt receipt = getMockReceipt();
    when(instructionPersisterService.saveAllInstruction(anyList()))
        .thenReturn(Collections.singletonList(instruction));
    doNothing()
        .when(containerPersisterService)
        .saveContainerAndContainerItems(anyList(), anyList());
    when(receiptService.saveReceipt(any(Receipt.class))).thenReturn(receipt);
    rdcReceivingUtils.persistReceivedContainerDetails(
        Collections.singletonList(instruction),
        Collections.singletonList(container),
        Collections.singletonList(containerItem),
        Collections.singletonList(receipt),
        Collections.emptyList());
    verify(instructionPersisterService, times(1)).saveAllInstruction(anyList());
    verify(containerPersisterService, times(1))
        .saveContainerAndContainerItems(anyList(), anyList());
    verify(receiptService, times(1)).saveAll(anyList());
  }

  @Test
  public void testPersistReceivedContainerDetailsWithOutboxEvents() {
    Instruction instruction = MockRdcInstruction.getInstruction();
    Container container = getMockContainer();
    ContainerItem containerItem = getMockContainerItem();
    Receipt receipt = getMockReceipt();
    Collection<OutboxEvent> outboxEvents = new ArrayList<>();
    outboxEvents.add(
        OutboxEvent.builder()
            .eventIdentifier("a232323223")
            .executionTs(Instant.now())
            .metaData(MetaData.with(ReceivingConstants.KEY, "a32323223"))
            .publisherPolicyId("test")
            .payloadRef(new PayloadRef())
            .build());
    when(instructionPersisterService.saveAllInstruction(anyList()))
        .thenReturn(Collections.singletonList(instruction));
    when(outboxEventSinkService.saveAllEvent(any())).thenReturn(true);
    doNothing()
        .when(containerPersisterService)
        .saveContainerAndContainerItems(anyList(), anyList());
    when(receiptService.saveReceipt(any(Receipt.class))).thenReturn(receipt);
    rdcReceivingUtils.persistReceivedContainerDetails(
        Collections.singletonList(instruction),
        Collections.singletonList(container),
        Collections.singletonList(containerItem),
        Collections.singletonList(receipt),
        Collections.emptyList());
    rdcReceivingUtils.persistOutboxEvents(outboxEvents);
    verify(instructionPersisterService, times(1)).saveAllInstruction(anyList());
    verify(containerPersisterService, times(1))
        .saveContainerAndContainerItems(anyList(), anyList());
    verify(receiptService, times(1)).saveAll(anyList());
    verify(outboxEventSinkService, times(1)).saveAllEvent(any());
  }

  @Test
  public void checkIfVendorComplianceRequired_EmptyTransportationModes() throws IOException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    rdcReceivingUtils.checkIfVendorComplianceRequired(
        instructionRequest, deliveryDocumentList.get(0), instructionResponse);
    assertNull(instructionResponse.getDeliveryDocuments());
  }

  @Test
  public void checkIfVendorComplianceRequired_LimitedQtyVerify() throws IOException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    instructionRequest.setRegulatedItemType(VendorCompliance.LIMITED_QTY);
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTransportationModes(getORMD());
    when(deliveryDocumentHelper.updateVendorCompliance(any(DeliveryDocumentLine.class)))
        .thenReturn(true);
    rdcReceivingUtils.checkIfVendorComplianceRequired(
        instructionRequest, deliveryDocuments.get(0), instructionResponse);
    assertNotNull(instructionResponse.getDeliveryDocuments());
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
  }

  @Test
  public void testGetContainersCountToBeReceivedInRDS_NONCONRTSPUT() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(RdcConstants.DA_CASE_PACK_NONCON_RTS_PUT_ITEM_HANDLING_CODE);
    int containerCount =
        rdcReceivingUtils.getContainersCountToBeReceived(deliveryDocumentLine, 10, null, false);
    assertEquals(containerCount, 1);
  }

  @Test
  public void testGetContainersCountToBeReceivedInRDS_MASTERBREAKPACK_CONVERYPICKS()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(RdcConstants.DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE);
    int containerCount =
        rdcReceivingUtils.getContainersCountToBeReceived(deliveryDocumentLine, 1, null, false);
    assertEquals(containerCount, 2);
  }

  @Test
  public void testGetContainersCountToBeReceived_BREAKPACK_AtlasDaItems() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(true);
    deliveryDocumentLine
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(RdcConstants.DA_BREAK_PACK_NON_CON_ITEM_HANDLING_CODE);
    int containerCount =
        rdcReceivingUtils.getContainersCountToBeReceived(deliveryDocumentLine, 1, null, false);
    assertEquals(containerCount, 2);
  }

  @Test
  public void testGetContainersCountToBeReceived_BREAKPACK_AtlasDaItemsForSlotting()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(true);
    deliveryDocumentLine
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(RdcConstants.DA_BREAK_PACK_NON_CON_ITEM_HANDLING_CODE);
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    SlotDetails slotDetails = new SlotDetails();
    slotDetails.setMaxPallet(2);
    slotDetails.setStockType("N");
    slotDetails.setSlotSize(72);
    slotDetails.setCrossReferenceDoor("000");
    receiveInstructionRequest.setSlotDetails(slotDetails);
    int containerCount =
        rdcReceivingUtils.getContainersCountToBeReceived(
            deliveryDocumentLine, 2, receiveInstructionRequest, false);
    assertEquals(containerCount, 4);
  }

  @Test
  public void testGetContainersCountToBeReceivedInRDS_QtyReceiving() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("BC");
    int containerCount =
        rdcReceivingUtils.getContainersCountToBeReceived(deliveryDocumentLine, 10, null, false);
    assertEquals(containerCount, 10);
  }

  @Test
  public void testGetContainersCountToBeReceivedAtlasDAItems_PalletPullByStore()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("CC");
    int containerCount =
        rdcReceivingUtils.getContainersCountToBeReceived(deliveryDocumentLine, 10, null, true);
    assertEquals(containerCount, 1);
  }

  @Test
  public void testGetContainersCountToBeReceivedInRDS_QtyReceiving_LessThanCaseIsTrue()
      throws IOException {
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setIsLessThanCase(Boolean.TRUE);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("BC");
    int containerCount =
        rdcReceivingUtils.getContainersCountToBeReceived(deliveryDocumentLine, 4, null, false);
    assertEquals(containerCount, 4);
  }

  @Test
  public void testGetContainersCountToBeReceivedInRDS_QtyReceiving_LessThanCaseIsFalse()
      throws IOException {
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setIsLessThanCase(Boolean.FALSE);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("BC");
    int containerCount =
        rdcReceivingUtils.getContainersCountToBeReceived(deliveryDocumentLine, 4, null, false);
    assertEquals(containerCount, 4);
  }

  @Test
  public void testGetContainersCountToBeReceivedInRDS_CasePackVoicePUT() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(RdcConstants.DA_CASEPACK_VOICE_PUT);

    int containerCount =
        rdcReceivingUtils.getContainersCountToBeReceived(deliveryDocumentLine, 20, null, false);
    assertEquals(containerCount, 1);
  }

  @Test
  public void testGetContainersCountToBeReceivedInRDS_ManualDASlotting() throws IOException {
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDoorNumber("100");
    SlotDetails slotDetails = new SlotDetails();
    slotDetails.setSlot("V8623");
    receiveInstructionRequest.setSlotDetails(slotDetails);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    int containerCount =
        rdcReceivingUtils.getContainersCountToBeReceived(
            deliveryDocumentLine, 30, receiveInstructionRequest, false);
    assertEquals(containerCount, 1);
  }

  @Test
  public void filterDADeliveryDocumentsTest() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMixedPO();
    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);
    List<DeliveryDocument> resultDADocuments =
        rdcReceivingUtils.filterDADeliveryDocuments(deliveryDocuments);
    verify(rdcInstructionUtils, times(2)).isDADocument(any(DeliveryDocument.class));
  }

  @Test
  public void updateQuantitiesBasedOnUOM_TestWithVnpk_Uom() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMixedPO();
    rdcReceivingUtils.updateQuantitiesBasedOnUOM(deliveryDocuments);
    assertEquals(
        ReceivingConstants.Uom.VNPK,
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getQtyUOM());
  }

  @Test
  public void updateQuantitiesBasedOnUOM_TestWithWhpkAndEach_Uom() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentForDAItemWithDifferentUOMQuantity();
    rdcReceivingUtils.updateQuantitiesBasedOnUOM(deliveryDocuments);
    assertEquals(
        ReceivingConstants.Uom.VNPK,
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getQtyUOM());
    assertEquals(
        ReceivingConstants.Uom.VNPK,
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(1).getQtyUOM());
    assertEquals(
        4, (int) deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getTotalOrderQty());
    assertEquals(
        1, (int) deliveryDocuments.get(0).getDeliveryDocumentLines().get(1).getTotalOrderQty());
    assertEquals(
        8, (int) deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getOverageQtyLimit());
    assertEquals(
        2, (int) deliveryDocuments.get(0).getDeliveryDocumentLines().get(1).getOverageQtyLimit());
  }

  @Test
  public void testBreakPackConveyPickItemInstruction() throws IOException {
    DeliveryDocumentLine deliveryDocumentLine =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0);
    deliveryDocumentLine
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(RdcConstants.DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE);
    deliveryDocumentLine.getAdditionalInfo().setHandlingCode("M");
    deliveryDocumentLine.getAdditionalInfo().setPackTypeCode("B");
    Instruction instruction =
        rdcReceivingUtils.getBreakPackInstruction(
            MockInstructionRequest.getInstructionRequest(), deliveryDocumentLine);
    assertNotNull(instruction);
    assertEquals(
        instruction.getInstructionCode(),
        RdcInstructionType.DA_RECEIVING_PACK_TYPE_VALIDATION.getInstructionCode());
    assertEquals(
        instruction.getInstructionMsg(),
        String.format(
            RdcConstants.BREAK_PACK_CONVEY_PICKS_MESSAGE, deliveryDocumentLine.getItemNbr()));
  }

  @Test
  public void testMasterBreakPackInstruction() throws IOException {
    DeliveryDocumentLine deliveryDocumentLine =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0);
    deliveryDocumentLine.getAdditionalInfo().setHandlingCode("C");
    deliveryDocumentLine
        .getAdditionalInfo()
        .setPackTypeCode(RdcConstants.MASTER_BREAK_PACK_TYPE_CODE);
    deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("PC");
    Instruction instruction =
        rdcReceivingUtils.getBreakPackInstruction(
            MockInstructionRequest.getInstructionRequest(), deliveryDocumentLine);
    assertNotNull(instruction);
    assertEquals(
        instruction.getInstructionCode(),
        RdcInstructionType.DA_RECEIVING_PACK_TYPE_VALIDATION.getInstructionCode());
    assertEquals(
        instruction.getInstructionMsg(),
        String.format(RdcConstants.MASTER_BREAK_PACK_MESSAGE, deliveryDocumentLine.getItemNbr()));
  }

  @Test
  public void getNonConItemInstruction_Success() {
    String handlingCode = "N";
    Instruction instruction =
        rdcReceivingUtils.getNonConInstruction("2324322", "000423232334", null, handlingCode);
    assertNotNull(instruction);
    assertEquals(
        instruction.getInstructionCode(),
        RdcInstructionType.DA_RECEIVING_PACK_TYPE_VALIDATION.getInstructionCode());
    assertEquals(
        instruction.getInstructionMsg(),
        String.format(
            RdcConstants.NON_CON_HANDLING_CODES_INFO_MESSAGE,
            RdcConstants.DA_NON_CON_HANDLING_CODES_MAP.get(handlingCode)));
  }

  @Test
  public void testCheckIfBreakPackConveyItem_Success() throws IOException {
    Long itemNumber = 3804890L;
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(RdcConstants.DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("M");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    rdcReceivingUtils.validateBreakPackItems(
        deliveryDocuments.get(0), MockRdcInstruction.getInstructionRequest(), instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertTrue(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getBreakPackValidationRequired());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.DA_RECEIVING_PACK_TYPE_VALIDATION.getInstructionCode());
    assertEquals(
        instructionResponse.getInstruction().getInstructionMsg(),
        String.format(RdcConstants.BREAK_PACK_CONVEY_PICKS_MESSAGE, itemNumber));
  }

  @Test
  public void testCheckIfMaxOverageReceived_OverageTrue() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    Pair<DeliveryDocument, Long> deliveryDocumentLongPair =
        new Pair<>(deliveryDocuments.get(0), 500L);
    Boolean maxOverageReceived =
        rdcReceivingUtils.checkIfMaxOverageReceived(
            deliveryDocumentLongPair.getKey(), deliveryDocumentLongPair.getValue(), 10);

    assertNotNull(maxOverageReceived);
    assertTrue(maxOverageReceived);
  }

  @Test
  public void testCheckIfMaxOverageReceived_OverageFalse() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    Pair<DeliveryDocument, Long> deliveryDocumentLongPair =
        new Pair<>(deliveryDocuments.get(0), 20L);
    Boolean maxOverageReceived =
        rdcReceivingUtils.checkIfMaxOverageReceived(
            deliveryDocumentLongPair.getKey(), deliveryDocumentLongPair.getValue(), 10);

    assertNotNull(maxOverageReceived);
    assertFalse(maxOverageReceived);
  }

  @Test
  public void testCheckAllDAPOsFulfilled_1PO_True() throws IOException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType();
    instructionRequest.setDeliveryDocuments(
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA());
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLineDA(400L);
    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(Boolean.TRUE);
    List<DeliveryDocument> checkAllDAPosFulfilled =
        rdcReceivingUtils.checkIfAllDAPosFulfilled(
            instructionRequest, receivedQuantityResponseFromRDS);

    assertNotNull(checkAllDAPosFulfilled);
    assertTrue(checkAllDAPosFulfilled.size() > 0);
  }

  @Test
  public void testCheckAllDAPOsFulfilled_1PO_False() throws IOException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType();
    instructionRequest.setDeliveryDocuments(
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA());
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLineDA(100L);
    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(Boolean.TRUE);
    List<DeliveryDocument> checkAllDAPosFulfilled =
        rdcReceivingUtils.checkIfAllDAPosFulfilled(
            instructionRequest, receivedQuantityResponseFromRDS);

    assertNotNull(checkAllDAPosFulfilled);
    assertTrue(checkAllDAPosFulfilled.size() > 0);
  }

  @Test
  public void testCheckAllDAPOsFulfilled_MultiplePOs_True() throws IOException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType();
    instructionRequest.setDeliveryDocuments(
        MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA());
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLineMultipleDA(400L);
    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(Boolean.TRUE);
    List<DeliveryDocument> checkAllDAPosFulfilled =
        rdcReceivingUtils.checkIfAllDAPosFulfilled(
            instructionRequest, receivedQuantityResponseFromRDS);

    assertNotNull(checkAllDAPosFulfilled);
    assertTrue(checkAllDAPosFulfilled.size() > 0);
  }

  @Test
  public void testCheckAllPOsFulfilled_MultiplePOs_False() throws IOException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType();
    instructionRequest.setDeliveryDocuments(
        MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA());
    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(Boolean.TRUE);
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLineMultipleDA(100L);
    List<DeliveryDocument> checkAllDAPosFulfilled =
        rdcReceivingUtils.checkIfAllDAPosFulfilled(
            instructionRequest, receivedQuantityResponseFromRDS);

    assertNotNull(checkAllDAPosFulfilled);
    assertTrue(checkAllDAPosFulfilled.size() > 0);
  }

  @Test
  public void testCheckIfBreakPackConveyItem_Failure() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemHandlingMethod("BC");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    rdcReceivingUtils.validateBreakPackItems(
        deliveryDocuments.get(0), MockRdcInstruction.getInstructionRequest(), instructionResponse);
    assertNull(instructionResponse.getInstruction());
    assertNull(instructionResponse.getDeliveryDocuments());
  }

  @Test
  public void testCheckIfBreakPackConveyItem_NotRequired() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemHandlingMethod("BC");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setBreakPackValidationRequired(Boolean.TRUE);
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    rdcReceivingUtils.validateBreakPackItems(
        deliveryDocuments.get(0), MockRdcInstruction.getInstructionRequest(), instructionResponse);
    assertNull(instructionResponse.getInstruction());
    assertNull(instructionResponse.getDeliveryDocuments());
  }

  @Test
  public void testCheckIfNonConHandlingCode_Success() throws IOException {
    Long itemNumber = 3804890L;
    String handlingCode = "N";
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode(handlingCode);
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    rdcReceivingUtils.checkIfNonConveyableItem(
        deliveryDocuments.get(0), MockRdcInstruction.getInstructionRequest(), instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertTrue(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getNonConValidationRequired());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.DA_RECEIVING_PACK_TYPE_VALIDATION.getInstructionCode());
    assertEquals(
        instructionResponse.getInstruction().getInstructionMsg(),
        String.format(
            RdcConstants.NON_CON_HANDLING_CODES_INFO_MESSAGE,
            RdcConstants.DA_NON_CON_HANDLING_CODES_MAP.get(handlingCode)));
  }

  @Test
  public void testCheckIfNonConHandlingCode_Failure() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    String handlingCode = "C";
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode(handlingCode);
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    rdcReceivingUtils.checkIfNonConveyableItem(
        deliveryDocuments.get(0), MockRdcInstruction.getInstructionRequest(), instructionResponse);
    assertNull(instructionResponse.getInstruction());
    assertNull(instructionResponse.getDeliveryDocuments());
  }

  @Test
  public void testCheckIfNonConHandlingCode_NotRequired() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("N");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setNonConValidationRequired(Boolean.TRUE);
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    rdcReceivingUtils.checkIfNonConveyableItem(
        deliveryDocuments.get(0), MockRdcInstruction.getInstructionRequest(), instructionResponse);
    assertNull(instructionResponse.getInstruction());
    assertNull(instructionResponse.getDeliveryDocuments());
  }

  @Test
  public void testOverrideItemPropertiesInDeliveryDocumentLine_Success() {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setDeliveryNumber(5454L);
    DeliveryDocumentLine deliveryDocumentLine = getMockDeliveryDocumentLine();
    Map<String, String> itemMiscInfo = new HashMap<>();
    itemMiscInfo.put(ReceivingConstants.TEMPORARY_HANDLING_METHOD_CODE, "M");
    DeliveryItemOverride mockDeliveryItemOverride = new DeliveryItemOverride();
    mockDeliveryItemOverride.setItemMiscInfo(itemMiscInfo);
    mockDeliveryItemOverride.setItemNumber(34533232L);
    mockDeliveryItemOverride.setDeliveryNumber(5454L);
    mockDeliveryItemOverride.setLastChangedTs(new Date());
    mockDeliveryItemOverride.setFacilityNum(32679);
    deliveryDocument.setDeliveryDocumentLines(Arrays.asList(deliveryDocumentLine));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_TEMP_ITEM_OVERRIDE_ENABLED,
            false))
        .thenReturn(true);
    when(deliveryItemOverrideService.findByItemNumber(34533232L))
        .thenReturn(Optional.of(mockDeliveryItemOverride));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_OVERRIDE_SAME_DAY_ALLOWED,
            false))
        .thenReturn(false);
    rdcReceivingUtils.overrideItemProperties(deliveryDocument);

    verify(deliveryItemOverrideService, times(1)).findByItemNumber(34533232L);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_OVERRIDE_SAME_DAY_ALLOWED,
            false);

    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode(), "B");
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getHandlingCode(), "M");
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode(), "BM");
    assertEquals(
        deliveryDocumentLine.getAdditionalInfo().getItemHandlingMethod(),
        "Breakpack Conveyable Picks");
  }

  @Test
  public void testOverrideItemPropertiesInDeliveryDocumentLine_whenThereIsNoRecordInDB() {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setDeliveryNumber(5454L);
    DeliveryDocumentLine deliveryDocumentLine = getMockDeliveryDocumentLine();
    deliveryDocument.setDeliveryDocumentLines(Arrays.asList(deliveryDocumentLine));
    when(deliveryItemOverrideService.findByItemNumber(34533232L)).thenReturn(Optional.empty());

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_TEMP_ITEM_OVERRIDE_ENABLED,
            false))
        .thenReturn(true);
    rdcReceivingUtils.overrideItemProperties(deliveryDocument);

    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode(), "B");
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getHandlingCode(), "C");
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode(), "BC");
    verify(deliveryItemOverrideService, times(1)).findByItemNumber(34533232L);
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_OVERRIDE_SAME_DAY_ALLOWED,
            false);
  }

  @Test
  public void
      testOverrideItemPropertiesInDeliveryDocumentLine_whenSameDayItemOverrideFlagIsDisabled() {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setDeliveryNumber(5454L);
    Map<String, String> itemMiscInfo = new HashMap<>();
    DeliveryDocumentLine deliveryDocumentLine = getMockDeliveryDocumentLine();
    itemMiscInfo.put(ReceivingConstants.TEMPORARY_PACK_TYPE_CODE, "B");
    itemMiscInfo.put(ReceivingConstants.TEMPORARY_HANDLING_METHOD_CODE, "M");
    DeliveryItemOverride mockDeliveryItemOverride = new DeliveryItemOverride();
    mockDeliveryItemOverride.setItemMiscInfo(itemMiscInfo);
    mockDeliveryItemOverride.setItemNumber(34533232L);
    mockDeliveryItemOverride.setDeliveryNumber(5454L);
    mockDeliveryItemOverride.setLastChangedTs(new Date());
    mockDeliveryItemOverride.setFacilityNum(32655);
    deliveryDocument.setDeliveryDocumentLines(Arrays.asList(deliveryDocumentLine));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_TEMP_ITEM_OVERRIDE_ENABLED,
            false))
        .thenReturn(true);
    when(deliveryItemOverrideService.findByItemNumber(34533232L))
        .thenReturn(Optional.of(mockDeliveryItemOverride));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_OVERRIDE_SAME_DAY_ALLOWED,
            false))
        .thenReturn(false);

    rdcReceivingUtils.overrideItemProperties(deliveryDocument);

    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode(), "B");
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getHandlingCode(), "M");
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode(), "BM");
    assertEquals(
        deliveryDocumentLine.getAdditionalInfo().getItemHandlingMethod(),
        "Breakpack Conveyable Picks");

    verify(deliveryItemOverrideService, times(1)).findByItemNumber(34533232L);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_OVERRIDE_SAME_DAY_ALLOWED,
            false);
  }

  @Test
  public void
      testOverrideItemPropertiesInDeliveryDocumentLine_whenSameDayItemOverrideFlagIsEnabled_EligibleToOverride() {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setDeliveryNumber(5454L);
    Map<String, String> itemMiscInfo = new HashMap<>();
    DeliveryDocumentLine deliveryDocumentLine = getMockDeliveryDocumentLine();
    itemMiscInfo.put(ReceivingConstants.TEMPORARY_PACK_TYPE_CODE, "B");
    itemMiscInfo.put(ReceivingConstants.TEMPORARY_HANDLING_METHOD_CODE, "M");
    DeliveryItemOverride mockDeliveryItemOverride = new DeliveryItemOverride();
    mockDeliveryItemOverride.setItemMiscInfo(itemMiscInfo);
    mockDeliveryItemOverride.setItemNumber(34533232L);
    mockDeliveryItemOverride.setDeliveryNumber(5454L);
    mockDeliveryItemOverride.setLastChangedTs(new Date());
    mockDeliveryItemOverride.setFacilityNum(32655);
    deliveryDocument.setDeliveryDocumentLines(Arrays.asList(deliveryDocumentLine));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_TEMP_ITEM_OVERRIDE_ENABLED,
            false))
        .thenReturn(true);
    when(deliveryItemOverrideService.findByItemNumber(34533232L))
        .thenReturn(Optional.of(mockDeliveryItemOverride));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_OVERRIDE_SAME_DAY_ALLOWED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getDCTimeZone(anyInt())).thenReturn("US/Central");

    rdcReceivingUtils.overrideItemProperties(deliveryDocument);

    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode(), "B");
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getHandlingCode(), "M");
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode(), "BM");
    assertEquals(
        deliveryDocumentLine.getAdditionalInfo().getItemHandlingMethod(),
        "Breakpack Conveyable Picks");

    verify(deliveryItemOverrideService, times(1)).findByItemNumber(34533232L);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_OVERRIDE_SAME_DAY_ALLOWED,
            false);
  }

  @Test
  public void
      testOverrideItemPropertiesInDeliveryDocumentLine_whenSameDayItemOverrideFlagIsEnabled_NotEligibleToOverride() {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setDeliveryNumber(5454L);
    Map<String, String> itemMiscInfo = new HashMap<>();
    Calendar c = Calendar.getInstance();
    c.add(Calendar.DATE, -5);
    DeliveryDocumentLine deliveryDocumentLine = getMockDeliveryDocumentLine();
    itemMiscInfo.put(ReceivingConstants.TEMPORARY_PACK_TYPE_CODE, "B");
    itemMiscInfo.put(ReceivingConstants.TEMPORARY_HANDLING_METHOD_CODE, "M");
    DeliveryItemOverride mockDeliveryItemOverride = new DeliveryItemOverride();
    mockDeliveryItemOverride.setItemMiscInfo(itemMiscInfo);
    mockDeliveryItemOverride.setItemNumber(34533232L);
    mockDeliveryItemOverride.setDeliveryNumber(5454L);
    mockDeliveryItemOverride.setLastChangedTs(c.getTime());
    mockDeliveryItemOverride.setFacilityNum(32655);
    deliveryDocument.setDeliveryDocumentLines(Arrays.asList(deliveryDocumentLine));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_TEMP_ITEM_OVERRIDE_ENABLED,
            false))
        .thenReturn(true);
    when(deliveryItemOverrideService.findByItemNumber(34533232L))
        .thenReturn(Optional.of(mockDeliveryItemOverride));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_OVERRIDE_SAME_DAY_ALLOWED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getDCTimeZone(anyInt())).thenReturn("US/Central");

    rdcReceivingUtils.overrideItemProperties(deliveryDocument);

    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode(), "B");
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getHandlingCode(), "C");
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode(), "BC");
    assertEquals(
        deliveryDocumentLine.getAdditionalInfo().getItemHandlingMethod(), "Breakpack Conveyable");

    verify(deliveryItemOverrideService, times(1)).findByItemNumber(34533232L);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_OVERRIDE_SAME_DAY_ALLOWED,
            false);
  }

  @Test
  public void testOverrideItemPropertiesInDeliveryDocumentLine_whenItemPropertiesAreEmptyInTable() {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setDeliveryNumber(5454L);
    DeliveryDocumentLine deliveryDocumentLine = getMockDeliveryDocumentLine();
    DeliveryItemOverride mockDeliveryItemOverride = new DeliveryItemOverride();
    mockDeliveryItemOverride.setItemNumber(345L);
    mockDeliveryItemOverride.setDeliveryNumber(5454L);
    mockDeliveryItemOverride.setLastChangedTs(new Date());
    mockDeliveryItemOverride.setFacilityNum(32655);
    deliveryDocument.setDeliveryDocumentLines(Arrays.asList(deliveryDocumentLine));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_TEMP_ITEM_OVERRIDE_ENABLED,
            false))
        .thenReturn(true);
    when(deliveryItemOverrideService.findByItemNumber(34533232L))
        .thenReturn(Optional.ofNullable(null));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_OVERRIDE_SAME_DAY_ALLOWED,
            false))
        .thenReturn(true);

    rdcReceivingUtils.overrideItemProperties(deliveryDocument);
    verify(deliveryItemOverrideService, times(1)).findByItemNumber(34533232L);
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_OVERRIDE_SAME_DAY_ALLOWED,
            false);

    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode(), "B");
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getHandlingCode(), "C");
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode(), "BC");
    assertEquals(
        deliveryDocumentLine.getAdditionalInfo().getItemHandlingMethod(), "Breakpack Conveyable");
  }

  @Test
  public void testOverrideItemPropertiesInDeliveryDocumentLine_SetPackTypeBasedOnBreakpackRatio() {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setDeliveryNumber(5454L);
    DeliveryDocumentLine deliveryDocumentLine = getMockDeliveryDocumentLine();
    Map<String, String> itemMiscInfo = new HashMap<>();
    itemMiscInfo.put(ReceivingConstants.TEMPORARY_PACK_TYPE_CODE, "C");
    DeliveryItemOverride mockDeliveryItemOverride = new DeliveryItemOverride();
    mockDeliveryItemOverride.setItemMiscInfo(itemMiscInfo);
    mockDeliveryItemOverride.setItemNumber(34533232L);
    mockDeliveryItemOverride.setDeliveryNumber(5454L);
    mockDeliveryItemOverride.setLastChangedTs(new Date());
    mockDeliveryItemOverride.setFacilityNum(32679);
    deliveryDocument.setDeliveryDocumentLines(Arrays.asList(deliveryDocumentLine));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_TEMP_ITEM_OVERRIDE_ENABLED,
            false))
        .thenReturn(true);
    when(deliveryItemOverrideService.findByItemNumber(34533232L))
        .thenReturn(Optional.of(mockDeliveryItemOverride));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_OVERRIDE_SAME_DAY_ALLOWED,
            false))
        .thenReturn(false);
    rdcReceivingUtils.overrideItemProperties(deliveryDocument);

    verify(deliveryItemOverrideService, times(1)).findByItemNumber(34533232L);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_OVERRIDE_SAME_DAY_ALLOWED,
            false);

    assertEquals("B", deliveryDocumentLine.getAdditionalInfo().getPackTypeCode());
    assertEquals("C", deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    assertEquals("BC", deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode());
    assertEquals(
        "Breakpack Conveyable", deliveryDocumentLine.getAdditionalInfo().getItemHandlingMethod());
  }

  @Test
  public void testReceiveContainers_GreaterThanMaxContainerCount_Async_HappyPath()
      throws IOException, ReceivingException, ExecutionException, InterruptedException {
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setDeliveryDocumentLines(
        deliveryDocuments.get(0).getDeliveryDocumentLines());
    when(nimRdsService.getReceiveContainersRequestBodyForDAReceiving(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            anyInt(),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(getMockRdsContainersRequest(15));
    doReturn(
            CompletableFuture.completedFuture(
                MockRdsResponse.getRdsResponseForDABreakConveyPacks()))
        .when(asyncNimRdsRestApiClient)
        .getReceivedContainers(any(ReceiveContainersRequestBody.class), anyMap());
    List<ReceivedContainer> receivedContainerList =
        rdcReceivingUtils.receiveContainers(
            1,
            getMockInstructionRequest(receiveInstructionRequest, headers),
            receiveInstructionRequest.getDeliveryDocumentLines().get(0),
            headers,
            receiveInstructionRequest);
    verify(nimRdsService, times(1))
        .getReceiveContainersRequestBodyForDAReceiving(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            anyInt(),
            any(ReceiveInstructionRequest.class));
    verify(asyncNimRdsRestApiClient, times(2))
        .getReceivedContainers(any(ReceiveContainersRequestBody.class), any(Map.class));
  }

  @Test
  public void testReceiveContainers_testHeaders_ReinductRoutingLabel_flibEligible()
      throws IOException, ReceivingException, ExecutionException, InterruptedException {
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setDeliveryDocumentLines(
        deliveryDocuments.get(0).getDeliveryDocumentLines());
    headers.add(IS_REINDUCT_ROUTING_LABEL, String.valueOf(Boolean.FALSE));
    when(nimRdsService.getReceiveContainersRequestBodyForDAReceiving(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            anyInt(),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(getMockRdsContainersRequest(15));
    doReturn(
            CompletableFuture.completedFuture(
                MockRdsResponse.getRdsResponseForDABreakConveyPacks()))
        .when(asyncNimRdsRestApiClient)
        .getReceivedContainers(any(ReceiveContainersRequestBody.class), anyMap());
    List<ReceivedContainer> receivedContainerList =
        rdcReceivingUtils.receiveContainers(
            1,
            getMockInstructionRequest(receiveInstructionRequest, headers),
            receiveInstructionRequest.getDeliveryDocumentLines().get(0),
            headers,
            receiveInstructionRequest);
    verify(nimRdsService, times(1))
        .getReceiveContainersRequestBodyForDAReceiving(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            anyInt(),
            any(ReceiveInstructionRequest.class));
    verify(asyncNimRdsRestApiClient, times(2))
        .getReceivedContainers(any(ReceiveContainersRequestBody.class), any(Map.class));
  }

  @Test
  public void testReceiveContainers_LessThanMaxContainerCount_HappyPath()
      throws IOException, ReceivingException, ExecutionException, InterruptedException {
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    when(nimRdsService.getReceiveContainersRequestBodyForDAReceiving(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            anyInt(),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(getMockRdsContainersRequest(5));
    doReturn(MockRdsResponse.getRdsResponseForDABreakConveyPacks())
        .when(nimRDSRestApiClient)
        .receiveContainers(any(ReceiveContainersRequestBody.class), anyMap());
    List<ReceivedContainer> receivedContainerList =
        rdcReceivingUtils.receiveContainers(
            5,
            getMockInstructionRequest(receiveInstructionRequest, headers),
            receiveInstructionRequest.getDeliveryDocumentLines().get(0),
            headers,
            receiveInstructionRequest);
    verify(nimRdsService, times(1))
        .getReceiveContainersRequestBodyForDAReceiving(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            anyInt(),
            any(ReceiveInstructionRequest.class));
    verify(nimRDSRestApiClient, times(1))
        .receiveContainers(any(ReceiveContainersRequestBody.class), any(Map.class));
  }

  @Test
  public void testReceiveContainers_LessThanMaxContainerCount_HappyPath_BreakPackConveyPicks()
      throws IOException, ExecutionException, InterruptedException {
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BM");
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setDeliveryDocumentLines(
        deliveryDocuments.get(0).getDeliveryDocumentLines());
    when(nimRdsService.getReceiveContainersRequestBodyForDAReceiving(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            anyInt(),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(getMockRdsContainersRequestConveyPicks(20));
    doReturn(MockRdsResponse.getRdsResponseForDABreakConveyPacks())
        .when(nimRDSRestApiClient)
        .receiveContainers(any(ReceiveContainersRequestBody.class), anyMap());

    rdcReceivingUtils.receiveContainers(
        5,
        getMockInstructionRequest(receiveInstructionRequest, headers),
        receiveInstructionRequest.getDeliveryDocumentLines().get(0),
        headers,
        receiveInstructionRequest);
    verify(nimRdsService, times(1))
        .getReceiveContainersRequestBodyForDAReceiving(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            anyInt(),
            any(ReceiveInstructionRequest.class));
    verify(nimRDSRestApiClient, times(1))
        .receiveContainers(any(ReceiveContainersRequestBody.class), any(Map.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_getReceivedContainerList_RdsError_LessThanMaxContainerCount_ThrowsException()
      throws IOException, ExecutionException, InterruptedException {
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    when(nimRdsService.getReceiveContainersRequestBodyForDAReceiving(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            anyInt(),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(getMockRdsContainersRequest(5));
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.INVALID_SLOTTING_REQ,
                ReceivingConstants.SLOTTING_RESOURCE_NIMRDS_RESPONSE_ERROR_MSG))
        .when(nimRDSRestApiClient)
        .receiveContainers(any(ReceiveContainersRequestBody.class), anyMap());
    List<ReceivedContainer> receivedContainerList =
        rdcReceivingUtils.receiveContainers(
            5,
            getMockInstructionRequest(receiveInstructionRequest, headers),
            receiveInstructionRequest.getDeliveryDocumentLines().get(0),
            headers,
            receiveInstructionRequest);

    verify(nimRDSRestApiClient, times(1))
        .receiveContainers(any(ReceiveContainersRequestBody.class), any(Map.class));
  }

  @Test
  public void testPopulateProblemReceivedQtyDetails() throws IOException {
    Instruction instruction = MockRdcInstruction.getInstruction();
    instruction.setProblemTagId("3232323");
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    when(problemRepository.findProblemLabelByProblemTagId(anyString()))
        .thenReturn(MockProblemResponse.getMockProblemLabel());
    when(instructionPersisterService
            .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
                anyString(), anyInt(), anyString()))
        .thenReturn(10L);
    rdcReceivingUtils.populateProblemReceivedQtyDetails(deliveryDocument, instruction);

    assertNotNull(deliveryDocument);
    assertEquals(
        deliveryDocument.getDeliveryDocumentLines().get(0).getTotalReceivedQty().intValue(), 10);
    assertEquals(
        deliveryDocument.getDeliveryDocumentLines().get(0).getTotalOrderQty().intValue(), 335);
  }

  @Test
  public void testCheckIfInstructionForRtsPut_Success() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForRtsPut();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(Boolean.TRUE);

    rdcReceivingUtils.validateRtsPutItems(
        deliveryDocuments.get(0),
        MockInstructionRequest.getInstructionRequestForRtsPut(),
        instructionResponse,
        headers);
    assertNotNull(instructionResponse.getInstruction());
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.NON_CON_RTS_PUT_RECEIVING.getInstructionCode());
    assertEquals(
        instructionResponse.getInstruction().getInstructionMsg(),
        RdcInstructionType.NON_CON_RTS_PUT_RECEIVING.getInstructionMsg());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testCheckIfInstructionForRtsPut_Failure() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForRtsPut();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString(), anyString()))
        .thenReturn(Boolean.FALSE);

    rdcReceivingUtils.validateRtsPutItems(
        deliveryDocuments.get(0),
        MockInstructionRequest.getInstructionRequestForRtsPut(),
        instructionResponse,
        headers);
    assertFalse(instructionResponse.getDeliveryDocuments().size() > 0);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testCheckIfInstructionForRtsPut_ScanToPrint() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForRtsPut();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(Boolean.TRUE);

    rdcReceivingUtils.validateRtsPutItems(
        deliveryDocuments.get(0),
        MockInstructionRequest.getInstructionRequestForRtsPutWithScanToPrintFeatureType(),
        instructionResponse,
        headers);
    assertFalse(instructionResponse.getDeliveryDocuments().size() > 0);
  }

  @Test
  public void labelToSorter_SYM_Alignment() {
    Container container = getMockContainerForSYMLabelType();
    when(rdcManagedConfig.getSymEligibleLabelType()).thenReturn("SYM0002");
    String labelType =
        rdcReceivingUtils.getLabelTypeForSorterDivert(
            getMockReceivedContainersWithSYMAlignment(), container);

    assertNotNull(labelType);
    assertEquals(labelType, "SYM0002");
  }

  @Test
  public void labelToSorter_DSDC() {
    Container container = getMockContainerForDSDCFreight();
    String labelType =
        rdcReceivingUtils.getLabelTypeForSorterDivert(
            getMockReceivedContainersWithSYMAlignment(), container);
    assertNotNull(labelType);
    assertEquals(labelType, "DSDC");
  }

  @Test
  public void testIsWhpkReceiving_ReturnsFalseForCC() throws IOException {
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setPackTypeCode("C");
    deliveryDocumentLine.getAdditionalInfo().setHandlingCode("C");
    deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("CC");

    Boolean isLessThanCase = rdcReceivingUtils.isWhpkReceiving(deliveryDocumentLine, null);
    assertFalse(isLessThanCase);
  }

  @Test
  public void testIsWhpkReceiving_ReturnsTrueForBMConveyPicks() throws IOException {
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setPackTypeCode("B");
    deliveryDocumentLine.getAdditionalInfo().setHandlingCode("M");
    deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("BM");

    Boolean isLessThanCase = rdcReceivingUtils.isWhpkReceiving(deliveryDocumentLine, null);
    assertTrue(isLessThanCase);
  }

  @Test
  public void testIsWhpkReceiving_ReturnsTrueForLessThanCase() throws IOException {
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setPackTypeCode("B");
    deliveryDocumentLine.getAdditionalInfo().setHandlingCode("C");
    deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("BC");
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setIsLessThanCase(true);
    Boolean isLessThanCase =
        rdcReceivingUtils.isWhpkReceiving(deliveryDocumentLine, receiveInstructionRequest);
    assertTrue(isLessThanCase);
  }

  @Test
  public void labelToSorter_SYM_Alignment_CCMConfig_NotAvailable() {
    Container container = getMockContainerForSYMLabelType();
    String labelType =
        rdcReceivingUtils.getLabelTypeForSorterDivert(
            getMockReceivedContainersWithSYMAlignment(), container);

    assertNotNull(labelType);
    assertEquals(labelType, "SYM00020");
  }

  @Test
  public void labelToSorter_SYM_Alignment_CCMConfig_IsEmpty() {
    Container container = getMockContainerForSYMLabelType();
    when(rdcManagedConfig.getSymEligibleLabelType()).thenReturn("");
    String labelType =
        rdcReceivingUtils.getLabelTypeForSorterDivert(
            getMockReceivedContainersWithSYMAlignment(), container);

    assertNotNull(labelType);
    assertEquals(labelType, "SYM00020");
  }

  @Test
  public void labelToSorter_With_STORE_Alignment() {
    Container container = getMockContainerForSYMLabelType();
    when(rdcManagedConfig.getSymEligibleLabelType()).thenReturn("SYM0002");
    String labelType =
        rdcReceivingUtils.getLabelTypeForSorterDivert(getMockReceivedContainers(), container);

    assertNotNull(labelType);
    assertEquals(labelType, LabelType.STORE.name());
  }

  @Test
  public void labelToSorter_With_PUT_Alignment() {
    Container container = getMockContainerForPUTLabelType();
    when(rdcManagedConfig.getSymEligibleLabelType()).thenReturn("SYM0002");
    String labelType =
        rdcReceivingUtils.getLabelTypeForSorterDivert(
            getMockReceivedContainersWithPUTAlignment(), container);

    assertNotNull(labelType);
    assertEquals(labelType, LabelType.PUT.name());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testValidateOverage() throws IOException {

    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    Integer receiveQty = 10;
    Pair<DeliveryDocument, Long> deliveryDocumentLongPair =
        new Pair<>(deliveryDocuments.get(0), 500L);
    when(rdcInstructionUtils.autoSelectDocumentAndDocumentLine(any(), anyInt(), anyString(), any()))
        .thenReturn(deliveryDocumentLongPair);
    rdcReceivingUtils.validateOverage(deliveryDocuments, receiveQty, headers, false);

    verify(rdcInstructionUtils, times(1))
        .autoSelectDocumentAndDocumentLine(anyList(), anyInt(), anyString(), headers);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testValidateOverageForLessthanACase() throws IOException {

    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    Integer receiveQty = 10;
    Pair<DeliveryDocument, Long> deliveryDocumentLongPair =
        new Pair<>(deliveryDocuments.get(0), 500L);
    when(rdcInstructionUtils.autoSelectDocumentAndDocumentLine(any(), anyInt(), anyString(), any()))
        .thenReturn(deliveryDocumentLongPair);
    rdcReceivingUtils.validateOverage(deliveryDocuments, receiveQty, headers, true);

    verify(rdcInstructionUtils, times(1))
        .autoSelectDocumentAndDocumentLine(anyList(), anyInt(), anyString(), headers);
    assertEquals((int) receiveQty, 0);
  }

  @Test
  public void getMasterBreakPackInstruction_Success() {

    Long itemNumber = 34533232L;
    Instruction instruction =
        rdcReceivingUtils.getMasterBreakPackInstruction(
            "2324322", "000423232334", null, itemNumber);
    assertNotNull(instruction);
    assertEquals(
        instruction.getInstructionCode(),
        RdcInstructionType.DA_RECEIVING_PACK_TYPE_VALIDATION.getInstructionCode());
    assertEquals(
        instruction.getInstructionMsg(),
        String.format(RdcConstants.BREAK_PACK_CONVEY_PICKS_MESSAGE, itemNumber));
  }

  private Receipt getMockReceipt() {
    Receipt receipt = new Receipt();
    receipt.setPurchaseReferenceNumber("2323234232");
    receipt.setPurchaseReferenceLineNumber(1);
    receipt.setDeliveryNumber(3232323L);
    receipt.setQuantity(43);
    return receipt;
  }

  private ReceiveInstructionRequest getMockReceiveInstructionRequest() throws IOException {
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDoorNumber("6");
    receiveInstructionRequest.setQuantity(25);
    receiveInstructionRequest.setDeliveryNumber(Long.valueOf("2356895623"));
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentForReceiveInstructionFromInstructionEntity();
    receiveInstructionRequest.setDeliveryDocuments(Arrays.asList(deliveryDocument));
    receiveInstructionRequest.setDeliveryDocumentLines(deliveryDocument.getDeliveryDocumentLines());
    receiveInstructionRequest.setIsLessThanCase(Boolean.FALSE);
    return receiveInstructionRequest;
  }

  private ReceiveInstructionRequest getMockReceiveInstructionRequest_LessThanCase()
      throws IOException {
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDoorNumber("6");
    receiveInstructionRequest.setQuantity(25);
    receiveInstructionRequest.setDeliveryNumber(Long.valueOf("2356895623"));
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentForReceiveInstructionFromInstructionEntity();
    receiveInstructionRequest.setDeliveryDocuments(Arrays.asList(deliveryDocument));
    receiveInstructionRequest.setDeliveryDocumentLines(deliveryDocument.getDeliveryDocumentLines());
    receiveInstructionRequest.setIsLessThanCase(Boolean.TRUE);
    return receiveInstructionRequest;
  }

  private InstructionRequest getMockInstructionRequest(
      ReceiveInstructionRequest receiveInstructionRequest, HttpHeaders httpHeaders) {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setUpcNumber(
        receiveInstructionRequest
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getCaseUpc());
    Long deliveryNumber =
        Objects.nonNull(receiveInstructionRequest.getDeliveryNumber())
            ? receiveInstructionRequest.getDeliveryNumber()
            : receiveInstructionRequest.getDeliveryDocuments().get(0).getDeliveryNumber();
    instructionRequest.setDeliveryNumber(deliveryNumber.toString());
    instructionRequest.setDoorNumber(receiveInstructionRequest.getDoorNumber());
    String messageId =
        Objects.nonNull(receiveInstructionRequest.getMessageId())
            ? receiveInstructionRequest.getMessageId()
            : httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY);
    instructionRequest.setMessageId(messageId);
    return instructionRequest;
  }

  private Container getMockContainer() {
    Container container = new Container();
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.BU_NUMBER, "02323");
    destination.put(ReceivingConstants.COUNTRY_CODE, "US");
    container.setDeliveryNumber(123L);
    container.setInstructionId(12345L);
    container.setTrackingId("lpn123");
    container.setParentTrackingId(null);
    container.setCreateUser("sysadmin");
    container.setCompleteTs(new Date());
    container.setLastChangedUser("sysadmin");
    container.setLastChangedTs(new Date());
    container.setPublishTs(new Date());
    container.setDestination(destination);
    return container;
  }

  private Container getMockContainerWithPickedStatus() {
    Container container = new Container();
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.BU_NUMBER, "02323");
    destination.put(ReceivingConstants.COUNTRY_CODE, "US");
    container.setDeliveryNumber(123L);
    container.setInstructionId(12345L);
    container.setTrackingId("lpn123");
    container.setParentTrackingId(null);
    container.setCreateUser("sysadmin");
    container.setCompleteTs(new Date());
    container.setLastChangedUser("sysadmin");
    container.setLastChangedTs(new Date());
    container.setPublishTs(new Date());
    container.setDestination(destination);
    container.setInventoryStatus(InventoryStatus.PICKED.name());
    return container;
  }

  private Container getMockContainerWithAllocatedStatus() {
    Container container = new Container();
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.BU_NUMBER, "02323");
    destination.put(ReceivingConstants.COUNTRY_CODE, "US");
    container.setDeliveryNumber(123L);
    container.setInstructionId(12345L);
    container.setTrackingId("lpn123");
    container.setParentTrackingId(null);
    container.setCreateUser("sysadmin");
    container.setCompleteTs(new Date());
    container.setLastChangedUser("sysadmin");
    container.setLastChangedTs(new Date());
    container.setPublishTs(new Date());
    container.setDestination(destination);
    container.setInventoryStatus(InventoryStatus.ALLOCATED.name());
    ContainerItem containerItem = getMockContainerItem();
    container.setContainerItems(Arrays.asList(containerItem));
    return container;
  }

  private List<Container> getMultipleContainers() {
    List<Container> containers = new ArrayList<>();
    Container container1 = new Container();
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.BU_NUMBER, "02323");
    destination.put(ReceivingConstants.COUNTRY_CODE, "US");
    container1.setDeliveryNumber(123L);
    container1.setInstructionId(12345L);
    container1.setTrackingId("lpn123");
    container1.setParentTrackingId(null);
    container1.setCreateUser("sysadmin");
    container1.setCompleteTs(new Date());
    container1.setLastChangedUser("sysadmin");
    container1.setLastChangedTs(new Date());
    container1.setPublishTs(new Date());
    container1.setDestination(destination);
    Container container2 = new Container();
    container2.setDeliveryNumber(123L);
    container2.setInstructionId(12345L);
    container2.setTrackingId("lpn1234");
    container2.setParentTrackingId(null);
    container2.setCreateUser("sysadmin");
    container2.setCompleteTs(new Date());
    container2.setLastChangedUser("sysadmin");
    container2.setLastChangedTs(new Date());
    container2.setPublishTs(new Date());
    container2.setDestination(destination);
    containers.add(container1);
    containers.add(container2);
    return containers;
  }

  private ContainerItem getMockContainerItem() {
    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId("lpn123");
    containerItem.setPurchaseReferenceNumber("PO1");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setItemNumber(12345678L);
    containerItem.setQuantity(80);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(4);
    containerItem.setWhpkQty(4);
    containerItem.setActualTi(5);
    containerItem.setActualHi(4);
    containerItem.setAsrsAlignment("SYM2_5");
    return containerItem;
  }

  private ReceiveContainersRequestBody getMockRdsContainersRequest(int containerCount) {
    ReceiveContainersRequestBody receiveContainersRequestBody = new ReceiveContainersRequestBody();
    List<ContainerOrder> containerOrders = new ArrayList<>();
    for (int i = 0; i < containerCount; i++) {
      ContainerOrder containerOrder = new ContainerOrder();
      containerOrder.setQty(1);
      containerOrder.setPoNumber("34232323");
      containerOrder.setPoLine(1);
      containerOrder.setBreakpackRatio(1);
      containerOrder.setDoorNum("423");
      containerOrder.setUserId("vr03fd4");
      containerOrders.add(containerOrder);
    }
    receiveContainersRequestBody.setContainerOrders(containerOrders);
    return receiveContainersRequestBody;
  }

  private ReceiveContainersRequestBody getMockRdsContainersRequestConveyPicks(int containerCount) {
    ReceiveContainersRequestBody receiveContainersRequestBody = new ReceiveContainersRequestBody();
    List<ContainerOrder> containerOrders = new ArrayList<>();
    for (int i = 0; i < containerCount; i++) {
      ContainerOrder containerOrder = new ContainerOrder();
      containerOrder.setQty(1);
      containerOrder.setPoNumber("34232323");
      containerOrder.setPoLine(1);
      containerOrder.setBreakpackRatio(4);
      containerOrder.setDoorNum("423");
      containerOrder.setUserId("vr03fd4");
      containerOrders.add(containerOrder);
    }
    receiveContainersRequestBody.setContainerOrders(containerOrders);
    return receiveContainersRequestBody;
  }

  public static List<TransportationModes> getORMD() {
    TransportationModes transportationModes = new TransportationModes();
    Mode mode = new Mode();
    mode.setCode(1);
    mode.setDescription("GROUND");
    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("ORM-D");
    dotHazardousClass.setDescription("Other Regulated Material");
    transportationModes.setDotRegionCode("UN");
    transportationModes.setMode(mode);
    transportationModes.setDotHazardousClass(dotHazardousClass);
    List<TransportationModes> transportationModesList = new ArrayList<>();
    transportationModesList.add(transportationModes);
    return transportationModesList;
  }

  public DeliveryDocumentLine getMockDeliveryDocumentLine() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setVendorPack(12);
    deliveryDocumentLine.setWarehousePack(2);
    ItemData additionalData = new ItemData();
    additionalData.setAtlasConvertedItem(false);
    additionalData.setPackTypeCode("B");
    additionalData.setHandlingCode("C");
    additionalData.setItemPackAndHandlingCode("BC");
    additionalData.setItemHandlingMethod("Breakpack Conveyable");
    deliveryDocumentLine.setAdditionalInfo(additionalData);
    deliveryDocumentLine.setItemNbr(34533232L);
    return deliveryDocumentLine;
  }

  public DeliveryDocumentLine getMockDeliveryDocumentLine_IncorrectPackTypeCode() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setVendorPack(12);
    deliveryDocumentLine.setWarehousePack(6);
    ItemData additionalData = new ItemData();
    additionalData.setAtlasConvertedItem(false);
    additionalData.setPackTypeCode("C");
    additionalData.setHandlingCode("C");
    additionalData.setItemPackAndHandlingCode("CC");
    additionalData.setItemHandlingMethod("CasePack Conveyable");
    deliveryDocumentLine.setAdditionalInfo(additionalData);
    deliveryDocumentLine.setItemNbr(34533232L);
    return deliveryDocumentLine;
  }

  public DeliveryDocumentLine getMockDeliveryDocumentLine_MasterCasePack() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setVendorPack(12);
    deliveryDocumentLine.setWarehousePack(6);
    ItemData additionalData = new ItemData();
    additionalData.setAtlasConvertedItem(false);
    additionalData.setPackTypeCode("C");
    additionalData.setHandlingCode("C");
    additionalData.setItemPackAndHandlingCode("MC");
    additionalData.setItemHandlingMethod("CasePack Conveyable");
    deliveryDocumentLine.setAdditionalInfo(additionalData);
    deliveryDocumentLine.setItemNbr(34533232L);
    return deliveryDocumentLine;
  }

  private ReceivedContainer getMockReceivedContainers() {
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setStoreAlignment("MANUAL");
    receivedContainer.setLabelTrackingId("r2323232308969587");
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setStore("03233");
    destination.setSlot("R8001");
    destination.setZone("A");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    return receivedContainer;
  }

  private List<ReceivedContainer> getMockReceivedContainersWithSymStoreAlignment() {
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setStoreAlignment("SYM2");
    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    receivedContainer.setLabelTrackingId("r2323232308969587");
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setStore("03233");
    destination.setSlot("R8001");
    destination.setZone("A");
    destinations.add(destination);
    receivedContainer.setLabelType(InventoryLabelType.R8000_DA_FULL_CASE.getType());
    receivedContainer.setRoutingLabel(true);
    receivedContainer.setDestinations(destinations);
    receivedContainers.add(receivedContainer);
    return receivedContainers;
  }

  private List<ReceivedContainer> getMockReceivedContainersForPalletPull() {
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setStoreAlignment("SYM2");
    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    receivedContainer.setLabelTrackingId("r2323232308969587");
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setStore("03233");
    destination.setSlot("R8001");
    destination.setZone("A");
    destinations.add(destination);
    receivedContainer.setLabelType(InventoryLabelType.R8000_DA_FULL_CASE.getType());
    receivedContainer.setFulfillmentMethod(
        FulfillmentMethodType.PALLET_PULL_RECEIVING_PARENT.getType());
    receivedContainer.setRoutingLabel(false);
    receivedContainer.setPalletPullByStore(true);
    receivedContainer.setDestinations(destinations);
    receivedContainers.add(receivedContainer);
    return receivedContainers;
  }

  private List<ReceivedContainer> getMockReceivedContainersForSYMStoreAlignment() {
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setStoreAlignment("SYM");
    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    receivedContainer.setLabelTrackingId("r2323232308969587");
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setStore("03233");
    destination.setSlot("R8001");
    destination.setZone("A");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    receivedContainers.add(receivedContainer);
    return receivedContainers;
  }

  private Container getMockContainerForSYMLabelType() {
    Container container = new Container();
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.BU_NUMBER, "02323");
    destination.put(ReceivingConstants.COUNTRY_CODE, "US");
    container.setDeliveryNumber(123L);
    container.setInstructionId(12345L);
    container.setTrackingId("r2323232308969587");
    container.setParentTrackingId(null);
    container.setCreateUser("sysadmin");
    container.setCompleteTs(new Date());
    container.setLastChangedUser("sysadmin");
    container.setLastChangedTs(new Date());
    container.setPublishTs(new Date());
    container.setDestination(destination);
    container.setInventoryStatus(InventoryStatus.ALLOCATED.name());
    return container;
  }

  private Container getMockContainerForDSDCFreight() {
    Container container = new Container();
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.BU_NUMBER, "02323");
    destination.put(ReceivingConstants.COUNTRY_CODE, "US");
    container.setDeliveryNumber(123L);
    container.setInstructionId(12345L);
    container.setTrackingId("r2323232308969587");
    container.setParentTrackingId(null);
    container.setCreateUser("sysadmin");
    container.setCompleteTs(new Date());
    container.setLastChangedUser("sysadmin");
    container.setLastChangedTs(new Date());
    container.setPublishTs(new Date());
    container.setDestination(destination);
    container.setSsccNumber("02232323232323");
    container.setInventoryStatus(InventoryStatus.PICKED.name());
    return container;
  }

  private Container getMockContainerForPUTLabelType() {
    Container container = new Container();
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.BU_NUMBER, "02323");
    destination.put(ReceivingConstants.COUNTRY_CODE, "US");
    container.setDeliveryNumber(123L);
    container.setInstructionId(12345L);
    container.setTrackingId("r2323232308969587");
    container.setParentTrackingId(null);
    container.setCreateUser("sysadmin");
    container.setCompleteTs(new Date());
    container.setLastChangedUser("sysadmin");
    container.setLastChangedTs(new Date());
    container.setPublishTs(new Date());
    container.setDestination(destination);
    container.setHasChildContainers(Boolean.TRUE);
    container.setInventoryStatus(InventoryStatus.ALLOCATED.name());
    return container;
  }

  private ReceivedContainer getMockReceivedContainersWithSYMAlignment() {
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setStoreAlignment("SYM2");
    receivedContainer.setLabelTrackingId("r2323232308969587");
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setStore("03233");
    destination.setSlot("R8001");
    destination.setZone("A");
    destinations.add(destination);
    receivedContainer.setLabelType(InventoryLabelType.R8000_DA_FULL_CASE.getType());
    receivedContainer.setRoutingLabel(true);
    return receivedContainer;
  }

  private ReceivedContainer getMockReceivedContainersWithPUTAlignment() {
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setStoreAlignment("PUT");
    receivedContainer.setLabelTrackingId("r2323232308969587");
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setStore("03233");
    destination.setSlot("R8001");
    destination.setZone("A");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    receivedContainer.setLabelType(InventoryLabelType.DA_BREAK_PACK_PUT_INDUCT.getType());
    return receivedContainer;
  }

  private ReceivedContainer getMockReceivedContainersWithSYMAlignmentAndDifferentTrackingId() {
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setStoreAlignment("SYM2");
    receivedContainer.setLabelTrackingId("r2323232308969586");
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setStore("03233");
    destination.setSlot("R8001");
    destination.setZone("A");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    receivedContainer.setLabelType(InventoryLabelType.R8000_DA_FULL_CASE.getType());
    return receivedContainer;
  }

  @Test
  public void testPostReceivingUpdates() throws Exception {
    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack().get(0);
    int receiveQty = 1;
    boolean isAtlasConvertedItem = true;
    List<ReceivedContainer> receivedContainers = getMockReceivedContainersWithSymStoreAlignment();

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_ENABLED_ON_KAFKA,
            false))
        .thenReturn(false);
    doNothing()
        .when(rdcContainerUtils)
        .postReceiptsToDcFin(any(Container.class), any(String.class));
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_KAFKA,
            false))
        .thenReturn(true);
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_JMS,
            false))
        .thenReturn(true);
    doNothing().when(jmsSorterPublisher).publishLabelToSorter(any(Container.class), anyString());

    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(getMockContainerForSYMLabelType());
    doNothing().when(rdcContainerUtils).publishContainersToInventory(any(Container.class));

    doNothing()
        .when(symboticPutawayPublishHelper)
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));

    rdcReceivingUtils.postReceivingUpdates(
        instruction,
        deliveryDocument,
        receiveQty,
        headers,
        isAtlasConvertedItem,
        receivedContainers,
        false);

    verify(rdcContainerUtils, times(0))
        .postReceiptsToDcFin(any(Container.class), any(String.class));
    verify(rdcContainerUtils, times(1)).publishContainersToInventory(any(Container.class));
    verify(symboticPutawayPublishHelper, times(1))
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));
    verify(instructionHelperService, times(1))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
  }

  @Test
  public void testPostReceivingUpdates_SplitChildContainers() throws Exception {
    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack().get(0);
    int receiveQty = 1;
    boolean isAtlasConvertedItem = true;
    List<ReceivedContainer> receivedContainers = getMockReceivedContainersWithSymStoreAlignment();
    receivedContainers.get(0).setLabelTrackingId("c326790000100000025655807");

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_ENABLED_ON_KAFKA,
            false))
        .thenReturn(false);

    doReturn(new JsonParser().parse("3"))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.CHILD_CONTAINER_LIMIT_FOR_INVENTORY);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CHILD_CONTAINER_SPLIT_ENABLED,
            false))
        .thenReturn(true);
    doNothing()
        .when(rdcContainerUtils)
        .postReceiptsToDcFin(any(Container.class), any(String.class));
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_KAFKA,
            false))
        .thenReturn(true);
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_JMS,
            false))
        .thenReturn(true);
    doNothing().when(jmsSorterPublisher).publishLabelToSorter(any(Container.class), anyString());

    File resources = new ClassPathResource("MockContainers_DA_BP_WithMultipleChild.json").getFile();
    String originalContainer = new String(Files.readAllBytes(resources.toPath()));
    List<Container> containers = Arrays.asList(gson.fromJson(originalContainer, Container[].class));

    Container mockConsolidatedContainer = containers.get(0);
    mockConsolidatedContainer.setContainerType(ContainerType.PALLET.name());
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(mockConsolidatedContainer);
    when(containerPersisterService.getContainerDetails(anyString())).thenReturn(containers.get(0));

    when(containerRepository.findAllByParentTrackingId(anyString()))
        .thenReturn(containers.get(0).getChildContainers());

    doReturn(getContainerItems()).when(containerItemRepository).findByTrackingIdIn(any());
    doNothing().when(rdcContainerUtils).publishContainersToInventory(any(Container.class));

    doNothing()
        .when(symboticPutawayPublishHelper)
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));

    rdcReceivingUtils.postReceivingUpdates(
        instruction,
        deliveryDocument,
        receiveQty,
        headers,
        isAtlasConvertedItem,
        receivedContainers,
        false);

    verify(rdcContainerUtils, times(4)).publishContainersToInventory(any(Container.class));
    verify(symboticPutawayPublishHelper, times(1))
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));
    verify(instructionHelperService, times(1))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
  }

  @Test
  public void testPostReceivingUpdates_SplitChildContainers_EventType_Offline() throws Exception {
    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack().get(0);
    int receiveQty = 1;
    boolean isAtlasConvertedItem = true;
    List<ReceivedContainer> receivedContainers = getMockReceivedContainersWithSymStoreAlignment();
    receivedContainers.get(0).setLabelTrackingId("c326790000100000025655807");

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_ENABLED_ON_KAFKA,
            false))
        .thenReturn(false);

    doReturn(new JsonParser().parse("3"))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.CHILD_CONTAINER_LIMIT_FOR_INVENTORY);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CHILD_CONTAINER_SPLIT_ENABLED,
            false))
        .thenReturn(true);
    doNothing()
        .when(rdcContainerUtils)
        .postReceiptsToDcFin(any(Container.class), any(String.class));
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_KAFKA,
            false))
        .thenReturn(true);
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_JMS,
            false))
        .thenReturn(true);
    doNothing().when(jmsSorterPublisher).publishLabelToSorter(any(Container.class), anyString());

    File resources = new ClassPathResource("MockContainers_DA_BP_WithMultipleChild.json").getFile();
    String originalContainer = new String(Files.readAllBytes(resources.toPath()));
    List<Container> containers = Arrays.asList(gson.fromJson(originalContainer, Container[].class));

    Container mockConsolidatedContainer = containers.get(0);
    mockConsolidatedContainer.setContainerType(ContainerType.PALLET.name());
    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(mockConsolidatedContainer);
    when(containerPersisterService.getContainerDetails(anyString())).thenReturn(containers.get(0));

    when(containerRepository.findAllByParentTrackingId(anyString()))
        .thenReturn(containers.get(0).getChildContainers());

    doReturn(getContainerItems()).when(containerItemRepository).findByTrackingIdIn(any());
    doNothing().when(rdcContainerUtils).publishContainersToInventory(any(Container.class));

    doNothing()
        .when(symboticPutawayPublishHelper)
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));

    rdcReceivingUtils.postReceivingUpdates(
        instruction,
        deliveryDocument,
        receiveQty,
        headers,
        isAtlasConvertedItem,
        receivedContainers,
        false);

    verify(rdcContainerUtils, times(1)).publishContainersToInventory(any(Container.class));
    verify(symboticPutawayPublishHelper, times(1))
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));
    verify(instructionHelperService, times(0))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
  }

  @Test
  public void testPostReceivingUpdates_SplitChildContainers_ContainerType_VendorPack()
      throws Exception {
    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack().get(0);
    int receiveQty = 1;
    boolean isAtlasConvertedItem = true;
    List<ReceivedContainer> receivedContainers = getMockReceivedContainersWithSymStoreAlignment();
    receivedContainers.get(0).setLabelTrackingId("c326790000100000025655807");

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_ENABLED_ON_KAFKA,
            false))
        .thenReturn(false);

    doReturn(new JsonParser().parse("3"))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.CHILD_CONTAINER_LIMIT_FOR_INVENTORY);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CHILD_CONTAINER_SPLIT_ENABLED,
            false))
        .thenReturn(true);
    doNothing()
        .when(rdcContainerUtils)
        .postReceiptsToDcFin(any(Container.class), any(String.class));
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_KAFKA,
            false))
        .thenReturn(true);
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_JMS,
            false))
        .thenReturn(true);
    doNothing().when(jmsSorterPublisher).publishLabelToSorter(any(Container.class), anyString());

    File resources = new ClassPathResource("MockContainers_DA_BP_WithMultipleChild.json").getFile();
    String originalContainer = new String(Files.readAllBytes(resources.toPath()));
    List<Container> containers = Arrays.asList(gson.fromJson(originalContainer, Container[].class));

    Container mockConsolidatedContainer = containers.get(0);
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(mockConsolidatedContainer);
    when(containerPersisterService.getContainerDetails(anyString())).thenReturn(containers.get(0));

    when(containerRepository.findAllByParentTrackingId(anyString()))
        .thenReturn(containers.get(0).getChildContainers());

    doReturn(getContainerItems()).when(containerItemRepository).findByTrackingIdIn(any());
    doNothing().when(rdcContainerUtils).publishContainersToInventory(any(Container.class));

    doNothing()
        .when(symboticPutawayPublishHelper)
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));

    rdcReceivingUtils.postReceivingUpdates(
        instruction,
        deliveryDocument,
        receiveQty,
        headers,
        isAtlasConvertedItem,
        receivedContainers,
        false);

    verify(rdcContainerUtils, times(1)).publishContainersToInventory(any(Container.class));
    verify(symboticPutawayPublishHelper, times(1))
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));
    verify(instructionHelperService, times(1))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
  }

  @Test
  public void testPostReceivingUpdates_SplitChildContainers_CCM_ChildSplit_disabled()
      throws Exception {
    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack().get(0);
    int receiveQty = 1;
    boolean isAtlasConvertedItem = true;
    List<ReceivedContainer> receivedContainers = getMockReceivedContainersWithSymStoreAlignment();
    receivedContainers.get(0).setLabelTrackingId("c326790000100000025655807");

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_ENABLED_ON_KAFKA,
            false))
        .thenReturn(false);

    doReturn(new JsonParser().parse("3"))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.CHILD_CONTAINER_LIMIT_FOR_INVENTORY);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CHILD_CONTAINER_SPLIT_ENABLED,
            false))
        .thenReturn(false);
    doNothing()
        .when(rdcContainerUtils)
        .postReceiptsToDcFin(any(Container.class), any(String.class));
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_KAFKA,
            false))
        .thenReturn(true);
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_JMS,
            false))
        .thenReturn(true);
    doNothing().when(jmsSorterPublisher).publishLabelToSorter(any(Container.class), anyString());

    File resources = new ClassPathResource("MockContainers_DA_BP_WithMultipleChild.json").getFile();
    String originalContainer = new String(Files.readAllBytes(resources.toPath()));
    List<Container> containers = Arrays.asList(gson.fromJson(originalContainer, Container[].class));

    Container mockConsolidatedContainer = containers.get(0);
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(mockConsolidatedContainer);
    when(containerPersisterService.getContainerDetails(anyString())).thenReturn(containers.get(0));

    when(containerRepository.findAllByParentTrackingId(anyString()))
        .thenReturn(containers.get(0).getChildContainers());

    doReturn(getContainerItems()).when(containerItemRepository).findByTrackingIdIn(any());
    doNothing().when(rdcContainerUtils).publishContainersToInventory(any(Container.class));

    doNothing()
        .when(symboticPutawayPublishHelper)
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));

    rdcReceivingUtils.postReceivingUpdates(
        instruction,
        deliveryDocument,
        receiveQty,
        headers,
        isAtlasConvertedItem,
        receivedContainers,
        false);

    verify(rdcContainerUtils, times(1)).publishContainersToInventory(any(Container.class));
    verify(symboticPutawayPublishHelper, times(1))
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));
    verify(instructionHelperService, times(1))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
  }

  @Test
  public void testPostReceivingUpdates_SplitChildContainers_CCM_ChildSplit_returns_null()
      throws Exception {
    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack().get(0);
    int receiveQty = 1;
    boolean isAtlasConvertedItem = true;
    List<ReceivedContainer> receivedContainers = getMockReceivedContainersWithSymStoreAlignment();
    receivedContainers.get(0).setLabelTrackingId("c326790000100000025655807");

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_ENABLED_ON_KAFKA,
            false))
        .thenReturn(false);

    doReturn(null)
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.CHILD_CONTAINER_LIMIT_FOR_INVENTORY);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CHILD_CONTAINER_SPLIT_ENABLED,
            false))
        .thenReturn(true);
    doNothing()
        .when(rdcContainerUtils)
        .postReceiptsToDcFin(any(Container.class), any(String.class));
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_KAFKA,
            false))
        .thenReturn(true);
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_JMS,
            false))
        .thenReturn(true);
    doNothing().when(jmsSorterPublisher).publishLabelToSorter(any(Container.class), anyString());

    File resources = new ClassPathResource("MockContainers_DA_BP_WithMultipleChild.json").getFile();
    String originalContainer = new String(Files.readAllBytes(resources.toPath()));
    List<Container> containers = Arrays.asList(gson.fromJson(originalContainer, Container[].class));

    Container mockConsolidatedContainer = containers.get(0);
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(mockConsolidatedContainer);
    when(containerPersisterService.getContainerDetails(anyString())).thenReturn(containers.get(0));

    when(containerRepository.findAllByParentTrackingId(anyString()))
        .thenReturn(containers.get(0).getChildContainers());

    doReturn(getContainerItems()).when(containerItemRepository).findByTrackingIdIn(any());
    doNothing().when(rdcContainerUtils).publishContainersToInventory(any(Container.class));

    doNothing()
        .when(symboticPutawayPublishHelper)
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));

    rdcReceivingUtils.postReceivingUpdates(
        instruction,
        deliveryDocument,
        receiveQty,
        headers,
        isAtlasConvertedItem,
        receivedContainers,
        false);

    verify(rdcContainerUtils, times(1)).publishContainersToInventory(any(Container.class));
    verify(symboticPutawayPublishHelper, times(1))
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));
    verify(instructionHelperService, times(1))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
  }

  @Test
  public void testPostReceivingUpdates_PalletPullDoNotSendSorterDivertUpdates() throws Exception {
    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack().get(0);
    int receiveQty = 10;
    boolean isAtlasConvertedItem = true;
    List<ReceivedContainer> receivedContainers = getMockReceivedContainersForPalletPull();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_ENABLED_ON_KAFKA,
            false))
        .thenReturn(false);
    doNothing()
        .when(rdcContainerUtils)
        .postReceiptsToDcFin(any(Container.class), any(String.class));
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(getMockContainerForSYMLabelType());
    doNothing().when(rdcContainerUtils).publishContainersToInventory(any(Container.class));

    doNothing()
        .when(symboticPutawayPublishHelper)
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));

    rdcReceivingUtils.postReceivingUpdates(
        instruction,
        deliveryDocument,
        receiveQty,
        headers,
        isAtlasConvertedItem,
        receivedContainers,
        false);

    verify(rdcContainerUtils, times(0))
        .postReceiptsToDcFin(any(Container.class), any(String.class));
    verify(rdcContainerUtils, times(1)).publishContainersToInventory(any(Container.class));
    verify(symboticPutawayPublishHelper, times(0))
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));
    verify(instructionHelperService, times(1))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
  }

  @Test(expectedExceptions = RuntimeException.class)
  public void testPostReceivingUpdatesPublishContainersToInventoryException() throws Exception {
    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack().get(0);
    int receiveQty = 1;
    boolean isAtlasConvertedItem = true;
    List<ReceivedContainer> receivedContainers = getMockReceivedContainersWithSymStoreAlignment();

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_ENABLED_ON_KAFKA,
            false))
        .thenReturn(false);
    doNothing()
        .when(rdcContainerUtils)
        .postReceiptsToDcFin(any(Container.class), any(String.class));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_KAFKA,
            false))
        .thenReturn(true);
    doNothing().when(kafkaAthenaPublisher).publishLabelToSorter(any(Container.class), anyString());
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(getMockContainerForSYMLabelType());
    doThrow(new RuntimeException())
        .when(rdcContainerUtils)
        .publishContainersToInventory(any(Container.class));

    doNothing()
        .when(symboticPutawayPublishHelper)
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));

    rdcReceivingUtils.postReceivingUpdates(
        instruction,
        deliveryDocument,
        receiveQty,
        headers,
        isAtlasConvertedItem,
        receivedContainers,
        false);

    verify(rdcContainerUtils, times(0))
        .postReceiptsToDcFin(any(Container.class), any(String.class));
    verify(jmsSorterPublisher, times(4)).publishLabelToSorter(any(Container.class), any());
    verify(rdcContainerUtils, times(2)).publishContainersToInventory(any(Container.class));
    verify(symboticPutawayPublishHelper, times(1))
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));
  }

  @Test
  public void testPostReceivingUpdatesWithPublishEI() throws Exception {
    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack().get(0);
    int receiveQty = 1;
    boolean isAtlasConvertedItem = true;
    List<ReceivedContainer> receivedContainers = getMockReceivedContainersWithSymStoreAlignment();

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_ENABLED_ON_KAFKA,
            false))
        .thenReturn(false);
    doNothing()
        .when(rdcContainerUtils)
        .postReceiptsToDcFin(any(Container.class), any(String.class));
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_KAFKA,
            false))
        .thenReturn(true);
    doNothing().when(kafkaAthenaPublisher).publishLabelToSorter(any(Container.class), anyString());

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_JMS,
            false))
        .thenReturn(true);
    doNothing().when(jmsSorterPublisher).publishLabelToSorter(any(Container.class), anyString());

    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(getMockContainerForSYMLabelType());
    doNothing().when(rdcContainerUtils).publishContainersToInventory(any(Container.class));

    doNothing()
        .when(symboticPutawayPublishHelper)
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    doNothing().when(rdcContainerUtils).publishContainerToEI(any(Container.class), any());

    rdcReceivingUtils.postReceivingUpdates(
        instruction,
        deliveryDocument,
        receiveQty,
        headers,
        isAtlasConvertedItem,
        receivedContainers,
        false);

    verify(rdcContainerUtils, times(0))
        .postReceiptsToDcFin(any(Container.class), any(String.class));
    verify(rdcContainerUtils, times(1)).publishContainersToInventory(any(Container.class));
    verify(symboticPutawayPublishHelper, times(1))
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false);
    verify(rdcContainerUtils, times(1)).publishContainerToEI(any(Container.class), any());
  }

  @Test
  public void testReceiveContainers_HappyPath_DASlotPallet()
      throws IOException, ExecutionException, InterruptedException {
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CN");
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    SlotDetails slotDetails = new SlotDetails();
    slotDetails.setSlot("A0002");
    receiveInstructionRequest.setQuantity(25);
    receiveInstructionRequest.setSlotDetails(slotDetails);
    receiveInstructionRequest.setDeliveryDocumentLines(
        deliveryDocuments.get(0).getDeliveryDocumentLines());
    when(nimRdsService.getReceiveContainersRequestBodyForDAReceiving(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            anyInt(),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(getMockSlottingResponseForRdsContainersRequest());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SMART_SLOTTING_ENABLED_ON_DA_ITEMS,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcSlottingUtils.receiveContainers(
            any(ReceiveInstructionRequest.class),
            nullable(String.class),
            any(HttpHeaders.class),
            any(ReceiveContainersRequestBody.class)))
        .thenReturn(getMockSlottingPalletResponseWithRdsContainers());
    doReturn(MockRdsResponse.getRdsResponseForDABreakConveyPacks())
        .when(nimRDSRestApiClient)
        .receiveContainers(any(ReceiveContainersRequestBody.class), anyMap());

    rdcReceivingUtils.receiveContainers(
        5,
        getMockInstructionRequest(receiveInstructionRequest, headers),
        receiveInstructionRequest.getDeliveryDocumentLines().get(0),
        headers,
        receiveInstructionRequest);
    verify(nimRdsService, times(1))
        .getReceiveContainersRequestBodyForDAReceiving(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            anyInt(),
            any(ReceiveInstructionRequest.class));
    verify(rdcSlottingUtils, times(1))
        .receiveContainers(
            any(ReceiveInstructionRequest.class),
            nullable(String.class),
            any(HttpHeaders.class),
            any(ReceiveContainersRequestBody.class));
    verify(nimRDSRestApiClient, times(0))
        .receiveContainers(any(ReceiveContainersRequestBody.class), any(Map.class));
  }

  public SlottingPalletResponse getMockSlottingPalletResponseWithRdsContainers() {
    SlottingPalletResponseWithRdsResponse slottingPalletResponseWithRdsResponse =
        new SlottingPalletResponseWithRdsResponse();
    ReceiveContainersResponseBody receiveContainersResponseBody =
        new ReceiveContainersResponseBody();
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId("9786855548");
    Destination destination = new Destination();
    destination.setSlot("J8k98");
    List<Destination> destinations = new ArrayList<>();
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    receivedContainers.add(receivedContainer);
    receiveContainersResponseBody.setReceived(receivedContainers);
    slottingPalletResponseWithRdsResponse.setRds(receiveContainersResponseBody);
    return slottingPalletResponseWithRdsResponse;
  }

  private ReceiveContainersRequestBody getMockSlottingResponseForRdsContainersRequest() {
    ReceiveContainersRequestBody receiveContainersRequestBody = new ReceiveContainersRequestBody();
    List<ContainerOrder> containerOrders = new ArrayList<>();
    ContainerOrder containerOrder = new ContainerOrder();
    containerOrder.setQty(1);
    containerOrder.setPoNumber("34232323");
    containerOrder.setPoLine(1);
    containerOrder.setBreakpackRatio(4);
    containerOrder.setDoorNum("423");
    containerOrder.setUserId("vr03fd4");
    containerOrders.add(containerOrder);

    SlottingOverride slottingOverride = new SlottingOverride();
    slottingOverride.setSlottingType(RdcConstants.RDS_SLOTTING_TYPE_MANUAL);
    slottingOverride.setSlotSize(60);
    slottingOverride.setSlot("K7289");
    slottingOverride.setXrefDoor(containerOrder.getDoorNum());
    containerOrder.setSlottingOverride(slottingOverride);
    receiveContainersRequestBody.setContainerOrders(containerOrders);
    return receiveContainersRequestBody;
  }

  @Test
  public void testBuildOutboxEvents_ShippingContainer_EI_RECEIVE_AND_PICKED_EVENTS()
      throws ReceivingException, IOException {
    ReceivedContainer receivedContainer = new ReceivedContainer();
    Instruction instruction = MockRdcInstruction.getInstruction();
    instruction.setReceivedQuantity(1);
    receivedContainer.setParentTrackingId(null);
    receivedContainer.setLabelTrackingId("trackingId");
    receivedContainer.setSorterDivertRequired(true);
    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    receivedContainers.add(receivedContainer);
    Container container = getMockContainerWithPickedStatus();
    container.setTrackingId("trackingId");
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(containerPersisterService.getConsolidatedContainerForPublish("trackingId"))
        .thenReturn(container);
    when(containerTransformer.transformList(Collections.singletonList(container)))
        .thenReturn(Collections.singletonList(new ContainerDTO()));
    when(outboxConfig.getKafkaPublisherPolicyInventory())
        .thenReturn("kafkaPublisherPolicyInventory");
    when(outboxConfig.getKafkaPublisherPolicyPutawayHawkeye())
        .thenReturn("kafkaPublisherPolicyPutawayHawkeye");
    when(outboxConfig.getKafkaPublisherPolicySorter()).thenReturn("kafkaPublisherPolicySorter");
    when(outboxConfig.getKafkaPublisherPolicyEIDCPickEvent())
        .thenReturn("kafkaPublisherPolicyEIDCPickEvent");
    when(outboxConfig.getKafkaPublisherPolicyWFT()).thenReturn("kafkaPublisherPolicyWFT");
    when(outboxConfig.getKafkaPublisherPolicyEIDCReceiveEvent())
        .thenReturn("kafkaPublisherPolicyEIDCReceiveEvent");
    doNothing().when(eiService).populateTickTickHeaders(any(), any(), any());
    Collection<OutboxEvent> outboxEvents =
        rdcReceivingUtils.buildOutboxEvents(
            receivedContainers, headers, instruction, deliveryDocument);
    assertNotNull(outboxEvents);
    assertEquals(5, outboxEvents.size());
    verify(containerPersisterService).getConsolidatedContainerForPublish("trackingId");
    verify(containerTransformer).transformList(Collections.singletonList(container));
    verifyNoMoreInteractions(containerPersisterService, containerTransformer);
    verify(eiService, times(2)).populateTickTickHeaders(any(), any(), any());
  }

  @Test
  public void testBuildOutboxEvents_EI_ALLOCATED_DC_RECEIVE_EVENTS()
      throws ReceivingException, IOException {
    ReceivedContainer receivedContainer = new ReceivedContainer();
    Instruction instruction = MockRdcInstruction.getInstruction();
    instruction.setReceivedQuantity(1);
    receivedContainer.setParentTrackingId(null);
    receivedContainer.setLabelTrackingId("trackingId");
    receivedContainer.setSorterDivertRequired(true);
    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    receivedContainers.add(receivedContainer);
    Container container = getMockContainerWithAllocatedStatus();
    container.setTrackingId("trackingId");
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    when(containerTransformer.transformList(Collections.singletonList(container)))
        .thenReturn(Collections.singletonList(new ContainerDTO()));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(outboxConfig.getKafkaPublisherPolicyInventory())
        .thenReturn("kafkaPublisherPolicyInventory");
    when(outboxConfig.getKafkaPublisherPolicyPutawayHawkeye())
        .thenReturn("kafkaPublisherPolicyPutawayHawkeye");
    when(outboxConfig.getKafkaPublisherPolicySorter()).thenReturn("kafkaPublisherPolicySorter");
    when(outboxConfig.getKafkaPublisherPolicyEIDCPickEvent())
        .thenReturn("kafkaPublisherPolicyEIDCPickEvent");
    when(outboxConfig.getKafkaPublisherPolicyWFT()).thenReturn("kafkaPublisherPolicyWFT");
    when(outboxConfig.getKafkaPublisherPolicyEIDCReceiveEvent())
        .thenReturn("kafkaPublisherPolicyEIDCReceiveEvent");
    doNothing().when(eiService).populateTickTickHeaders(any(), any(), any());
    Collection<OutboxEvent> outboxEvents =
        rdcReceivingUtils.buildOutboxEvents(
            receivedContainers, headers, instruction, deliveryDocument);
    assertNotNull(outboxEvents);
    assertEquals(4, outboxEvents.size());
    verify(containerPersisterService).getConsolidatedContainerForPublish(anyString());
    verify(containerTransformer).transformList(Collections.singletonList(container));
    verifyNoMoreInteractions(containerPersisterService, containerTransformer);
    verify(eiService, times(1)).populateTickTickHeaders(any(), any(), any());
  }

  @Test
  public void testBuildOutboxEvents_RoutingContainer_EI_RECEIVE_EVENTS_WithPutawayMessage()
      throws ReceivingException, IOException {
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelType(InventoryLabelType.R8000_DA_FULL_CASE.getType());
    receivedContainer.setPoNumber("23232323");
    receivedContainer.setPoLine(1);
    receivedContainer.setLabelTrackingId("trackingId");
    receivedContainer.setStoreAlignment("SYM2_5");
    receivedContainer.setRoutingLabel(true);
    receivedContainer.setSorterDivertRequired(true);
    Instruction instruction = MockRdcInstruction.getInstruction();
    instruction.setReceivedQuantity(1);
    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    receivedContainers.add(receivedContainer);
    Container container = getMockContainerWithAllocatedStatus();
    container.setTrackingId("trackingId");
    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId("trackingId");
    containerItem.setAsrsAlignment("SYM2_5");
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(containerPersisterService.getConsolidatedContainerForPublish("trackingId"))
        .thenReturn(container);
    when(containerTransformer.transformList(Collections.singletonList(container)))
        .thenReturn(Collections.singletonList(new ContainerDTO()));
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), any(Integer.class)))
        .thenReturn(containerItem);
    when(outboxConfig.getKafkaPublisherPolicyInventory())
        .thenReturn("kafkaPublisherPolicyInventory");
    when(outboxConfig.getKafkaPublisherPolicyPutawayHawkeye())
        .thenReturn("kafkaPublisherPolicyPutawayHawkeye");
    when(outboxConfig.getKafkaPublisherPolicySorter()).thenReturn("kafkaPublisherPolicySorter");
    when(outboxConfig.getKafkaPublisherPolicyEIDCPickEvent())
        .thenReturn("kafkaPublisherPolicyEIDCPickEvent");
    when(outboxConfig.getKafkaPublisherPolicyWFT()).thenReturn("kafkaPublisherPolicyWFT");
    when(outboxConfig.getKafkaPublisherPolicyEIDCReceiveEvent())
        .thenReturn("kafkaPublisherPolicyEIDCReceiveEvent");
    doNothing().when(eiService).populateTickTickHeaders(any(), any(), any());
    Collection<OutboxEvent> outboxEvents =
        rdcReceivingUtils.buildOutboxEvents(
            receivedContainers, headers, instruction, deliveryDocument);
    assertNotNull(outboxEvents);
    assertEquals(5, outboxEvents.size());

    verify(containerPersisterService).getConsolidatedContainerForPublish("trackingId");
    verify(containerTransformer).transformList(Collections.singletonList(container));
    verifyNoMoreInteractions(containerPersisterService, containerTransformer);
    verify(eiService, times(1)).populateTickTickHeaders(any(), any(), any());
  }

  @Test
  public void testPostCancelContainersUpdates() {
    Receipt adjustedReceipt = new Receipt();
    List<LabelData> labelDataList = new ArrayList<>();
    LabelData labelData = new LabelData();
    labelData.setTrackingId("232323223");
    labelDataList.add(labelData);
    Container container = new Container();
    Instruction instruction = new Instruction();
    Collection<OutboxEvent> outboxEvents = new ArrayList<>();
    outboxEvents.add(OutboxEvent.builder().build());
    when(receiptService.saveReceipt(adjustedReceipt)).thenReturn(adjustedReceipt);
    when(labelDataService.saveAll(labelDataList)).thenReturn(labelDataList);
    when(containerPersisterService.saveContainer(container)).thenReturn(container);
    when(instructionPersisterService.saveInstruction(instruction)).thenReturn(instruction);
    when(outboxEventSinkService.saveAllEvent(outboxEvents)).thenReturn(true);
    rdcReceivingUtils.postCancelContainersUpdates(
        adjustedReceipt, labelDataList, container, instruction, outboxEvents);
    verify(receiptService).saveReceipt(adjustedReceipt);
    verify(labelDataService).saveAll(labelDataList);
    verify(containerPersisterService).saveContainer(container);
    verify(receiptService).saveReceipt(adjustedReceipt);
    verify(outboxEventSinkService).saveAllEvent(outboxEvents);
  }

  @Test
  public void testBuildOutboxEventsForCancelContainers() throws Exception {
    Container container = createContainer();
    container.getContainerItems().get(0).setInboundChannelMethod("CROSSU");
    container
        .getContainerItems()
        .get(0)
        .setAsrsAlignment(ReceivingConstants.SYM_CASE_PACK_ASRS_VALUE);
    container.getContainerItems().get(0).setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);
    when(appConfig.getValidSymAsrsAlignmentValues())
        .thenReturn(Arrays.asList(ReceivingConstants.SYM_CASE_PACK_ASRS_VALUE));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(inventoryTransformer.transformToInventory(any(Container.class), anyString()))
        .thenReturn(new InventoryDetails());
    doNothing().when(eiService).populateTickTickHeaders(any(), any(), any());
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    when(outboxConfig.getKafkaPublisherPolicyPutawayHawkeye())
        .thenReturn("kafkaPublisherPolicyPutawayHawkeye");
    when(outboxConfig.getKafkaPublisherPolicyEIDCVoidEvent())
        .thenReturn("kafkaPublisherPolicyEIDCVoidEvent");
    when(outboxConfig.getKafkaPublisherPolicyWFT()).thenReturn("kafkaPublisherPolicyWFT");
    rdcReceivingUtils.buildOutboxEventsForCancelContainers(container, headers, null);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false);
    verify(inventoryTransformer, times(1)).transformToInventory(any(Container.class), anyString());
    verify(eiService, times(1)).populateTickTickHeaders(any(), any(), any());
    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    verify(appConfig, times(1)).getValidSymAsrsAlignmentValues();
  }

  @Test
  public void testBuildOutboxEventsForCancelContainersWithChildContainers() throws Exception {
    Container container = createContainer();
    container.setChildContainers(new HashSet<>(Arrays.asList(new Container())));
    container.getContainerItems().get(0).setInboundChannelMethod("CROSSU");
    container
        .getContainerItems()
        .get(0)
        .setAsrsAlignment(ReceivingConstants.SYM_CASE_PACK_ASRS_VALUE);
    container.getContainerItems().get(0).setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);
    when(appConfig.getValidSymAsrsAlignmentValues())
        .thenReturn(Arrays.asList(ReceivingConstants.SYM_CASE_PACK_ASRS_VALUE));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(inventoryTransformer.transformToInventory(any(Container.class), anyString()))
        .thenReturn(new InventoryDetails());
    doNothing().when(eiService).populateTickTickHeaders(any(), any(), any());
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    when(outboxConfig.getKafkaPublisherPolicyPutawayHawkeye())
        .thenReturn("kafkaPublisherPolicyPutawayHawkeye");
    when(outboxConfig.getKafkaPublisherPolicyEIDCVoidEvent())
        .thenReturn("kafkaPublisherPolicyEIDCVoidEvent");
    when(outboxConfig.getKafkaPublisherPolicyWFT()).thenReturn("kafkaPublisherPolicyWFT");
    rdcReceivingUtils.buildOutboxEventsForCancelContainers(container, headers, null);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false);
    verify(inventoryTransformer, times(1)).transformToInventory(any(Container.class), anyString());
    verify(eiService, times(1)).populateTickTickHeaders(any(), any(), any());
    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    verify(appConfig, times(1)).getValidSymAsrsAlignmentValues();
  }

  private Container createContainer() {
    Container container = new Container();
    container.setParentTrackingId(null);
    container.setTrackingId("lpn1");
    container.setDeliveryNumber(12345L);
    container.setLocation("200");
    container.setInstructionId(123L);

    ContainerItem containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("PO1");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setInboundChannelMethod(PurchaseReferenceType.SSTKU.name());

    containerItem.setQuantity(6);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);
    container.setContainerItems(Collections.singletonList(containerItem));

    container.setPublishTs(new Date());
    container.setCompleteTs(new Date());
    container.setLastChangedUser("sysadmin");

    return container;
  }

  @Test
  public void testBuildOutboxEventsForWftCancelContainer() throws Exception {
    Container container = createContainer();
    container.getContainerItems().get(0).setInboundChannelMethod("CROSSU");
    container
        .getContainerItems()
        .get(0)
        .setAsrsAlignment(ReceivingConstants.SYM_CASE_PACK_ASRS_VALUE);
    container.getContainerItems().get(0).setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);
    container.getContainerItems().get(0).setQuantity(10);
    container.getContainerItems().get(0).setVnpkQty(10);
    container.getContainerItems().get(0).setWhpkQty(10);
    when(appConfig.getValidSymAsrsAlignmentValues())
        .thenReturn(Arrays.asList(ReceivingConstants.SYM_CASE_PACK_ASRS_VALUE));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(inventoryTransformer.transformToInventory(any(Container.class), anyString()))
        .thenReturn(new InventoryDetails());
    doNothing().when(eiService).populateTickTickHeaders(any(), any(), any());
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    when(rdcInstructionUtils.prepareInstructionMessage(
            any(ContainerItem.class),
            any(LabelAction.class),
            any(Integer.class),
            any(HttpHeaders.class)))
        .thenReturn(new PublishInstructionSummary());
    when(outboxConfig.getKafkaPublisherPolicyPutawayHawkeye())
        .thenReturn("kafkaPublisherPolicyPutawayHawkeye");
    when(outboxConfig.getKafkaPublisherPolicyEIDCVoidEvent())
        .thenReturn("kafkaPublisherPolicyEIDCVoidEvent");
    when(outboxConfig.getKafkaPublisherPolicyWFT()).thenReturn("kafkaPublisherPolicyWFT");
    rdcReceivingUtils.buildOutboxEventsForCancelContainers(
        container, headers, LabelAction.CORRECTION);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false);
    verify(inventoryTransformer, times(1)).transformToInventory(any(Container.class), anyString());
    verify(eiService, times(1)).populateTickTickHeaders(any(), any(), any());
    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    verify(appConfig, times(1)).getValidSymAsrsAlignmentValues();
    verify(locationService, times(0)).getLocationInfoByIdentifier(any(), any());
    verify(rdcContainerUtils, times(0)).setLocationHeaders(any(), any());
    verify(rdcInstructionUtils, times(1))
        .prepareInstructionMessage(
            any(ContainerItem.class),
            any(LabelAction.class),
            any(Integer.class),
            any(HttpHeaders.class));
  }

  @Test
  public void prepareInstructionMessageForDANonConTest() {
    Instruction instruction = MockRdcInstruction.getInstruction();
    instruction.setActivityName(WFTInstruction.NON_CON_CASEPACK.getActivityName());
    HttpHeaders headers = new HttpHeaders();
    headers.add(ReceivingConstants.USER_ID_HEADER_KEY, "key1");
    headers.add(ReceivingConstants.SECURITY_HEADER_KEY, "key2");
    rdcReceivingUtils.prepareInstructionMessage(
        instruction, getMockDeliveryDocumentLine(), 1, headers, false);
    assertEquals(instruction.getActivityName(), WFTInstruction.DA.getActivityName());
  }

  @Test
  public void prepareInstructionMessageForDAConTest() {
    Instruction instruction = MockRdcInstruction.getInstruction();
    instruction.setActivityName(WFTInstruction.DA.getActivityName());
    HttpHeaders headers = new HttpHeaders();
    headers.add(ReceivingConstants.USER_ID_HEADER_KEY, "key1");
    headers.add(ReceivingConstants.SECURITY_HEADER_KEY, "key2");
    rdcReceivingUtils.prepareInstructionMessage(
        instruction, getMockDeliveryDocumentLine(), 1, headers, false);
    assertEquals(instruction.getActivityName(), WFTInstruction.DA.getActivityName());
  }

  @Test
  public void testBuildOutboxEventsForWftCancelContainerAndWFTEmptyHeaders() throws Exception {
    Container container = createContainer();
    container.getContainerItems().get(0).setInboundChannelMethod("CROSSU");
    container
        .getContainerItems()
        .get(0)
        .setAsrsAlignment(ReceivingConstants.SYM_CASE_PACK_ASRS_VALUE);
    container.getContainerItems().get(0).setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);
    container.getContainerItems().get(0).setQuantity(10);
    container.getContainerItems().get(0).setVnpkQty(10);
    container.getContainerItems().get(0).setWhpkQty(10);
    when(appConfig.getValidSymAsrsAlignmentValues())
        .thenReturn(Arrays.asList(ReceivingConstants.SYM_CASE_PACK_ASRS_VALUE));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(inventoryTransformer.transformToInventory(any(Container.class), anyString()))
        .thenReturn(new InventoryDetails());
    doNothing().when(eiService).populateTickTickHeaders(any(), any(), any());
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    when(locationService.getLocationInfoByIdentifier(any(), any())).thenReturn(new LocationInfo());
    doNothing().when(rdcContainerUtils).setLocationHeaders(any(), any());
    when(rdcInstructionUtils.prepareInstructionMessage(
            any(ContainerItem.class),
            any(LabelAction.class),
            any(Integer.class),
            any(HttpHeaders.class)))
        .thenReturn(new PublishInstructionSummary());
    headers.remove(RdcConstants.WFT_LOCATION_ID);
    headers.remove(RdcConstants.WFT_LOCATION_TYPE);
    headers.remove(RdcConstants.WFT_SCC_CODE);
    when(outboxConfig.getKafkaPublisherPolicyPutawayHawkeye())
        .thenReturn("kafkaPublisherPolicyPutawayHawkeye");
    when(outboxConfig.getKafkaPublisherPolicyEIDCVoidEvent())
        .thenReturn("kafkaPublisherPolicyEIDCVoidEvent");
    when(outboxConfig.getKafkaPublisherPolicyWFT()).thenReturn("kafkaPublisherPolicyWFT");
    rdcReceivingUtils.buildOutboxEventsForCancelContainers(
        container, headers, LabelAction.CORRECTION);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false);
    verify(inventoryTransformer, times(1)).transformToInventory(any(Container.class), anyString());
    verify(eiService, times(1)).populateTickTickHeaders(any(), any(), any());
    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    verify(appConfig, times(1)).getValidSymAsrsAlignmentValues();
    verify(locationService, times(1)).getLocationInfoByIdentifier(any(), any());
    verify(rdcContainerUtils, times(1)).setLocationHeaders(any(), any());
    verify(rdcInstructionUtils, times(1))
        .prepareInstructionMessage(
            any(ContainerItem.class),
            any(LabelAction.class),
            any(Integer.class),
            any(HttpHeaders.class));
  }

  @Test
  public void testShouldIgnoreAutomationProcessing_disabledFeature() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            RdcConstants.IS_AUTOMATION_DELIVERY_FILTER_ENABLED))
        .thenReturn(false);
    assertTrue(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(12345L, 12345L));
  }

  @Test
  public void testShouldIgnoreAutomationProcessing_DeliveryAndItemAvailable() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(any(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(rdcManagedConfig.getValidAutomationDeliveries()).thenReturn(Arrays.asList("123", "1234"));
    when(rdcManagedConfig.getValidAutomationItems()).thenReturn(Arrays.asList("123", "1234"));
    assertTrue(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(123L, 1234L));
    assertTrue(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(1234L, 123L));
  }

  @Test
  public void testShouldIgnoreAutomationProcessing_DeliveryAvailableAndItemNotAvailable() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(any(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(rdcManagedConfig.getValidAutomationDeliveries()).thenReturn(Arrays.asList("123", "1234"));
    when(rdcManagedConfig.getValidAutomationItems()).thenReturn(Arrays.asList("12345", "1234"));
    assertFalse(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(123L, 123L));
    assertFalse(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(1232L, 123L));
  }

  @Test
  public void testShouldIgnoreAutomationProcessing_DeliveryListIsEmpty_PilotFlowEnabled() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(any(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(rdcManagedConfig.getValidAutomationItems()).thenReturn(Arrays.asList("12345", "1234"));
    when(rdcManagedConfig.getValidAutomationDeliveries()).thenReturn(Collections.emptyList());
    assertTrue(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(123L, 123L));
  }

  @Test
  public void testShouldIgnoreAutomationProcessing_DeliveryListIsEmpty_PilotFlowDisabled() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(any(), anyString(), anyBoolean()))
        .thenReturn(false);
    when(rdcManagedConfig.getValidAutomationItems()).thenReturn(Arrays.asList("12345", "1234"));
    when(rdcManagedConfig.getValidAutomationDeliveries()).thenReturn(Collections.emptyList());
    assertTrue(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(123L, 123L));
  }

  @Test
  public void testShouldIgnoreAutomationProcessing_ItemsListEmpty() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(any(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(rdcManagedConfig.getValidAutomationDeliveries()).thenReturn(Arrays.asList("123", "1234"));
    when(rdcManagedConfig.getValidAutomationItems()).thenReturn(Collections.emptyList());
    assertTrue(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(123L, 123L));
  }

  @Test
  public void testShouldIgnoreAutomationProcessing_DeliveriesAndItemsAreEmpty() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(any(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(rdcManagedConfig.getValidAutomationItems()).thenReturn(Collections.emptyList());
    when(rdcManagedConfig.getValidAutomationDeliveries()).thenReturn(Collections.emptyList());
    assertTrue(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(123L, 123L));
  }

  @Test
  public void testPublishOutBoxRdcLabelData() {
    ACLLabelDataTO aclLabelDataTO =
        ACLLabelDataTO.builder().deliveryNbr(5454L).groupNumber("5454").build();
    when(outboxConfig.getKafkaPublisherHawkeyeLabelData())
        .thenReturn("kafkaPublisherHawkeyeLabelData");
    rdcReceivingUtils.publishAutomationOutboxRdcLabelData(headers, aclLabelDataTO);
    verify(outboxEventSinkService).saveAllEvent(any());
  }

  @Test
  public void testAutomationBuildOutboxEvents() throws ReceivingException, IOException {
    ReceivedContainer receivedContainer = new ReceivedContainer();
    Instruction instruction = MockRdcInstruction.getInstruction();
    instruction.setReceivedQuantity(1);
    receivedContainer.setParentTrackingId(null);
    receivedContainer.setLabelTrackingId("trackingId");
    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    receivedContainers.add(receivedContainer);
    Container container = getMockContainerWithPickedStatus();
    container.setTrackingId("trackingId");
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    LabelData labelData = new LabelData();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(containerPersisterService.getConsolidatedContainerForPublish("trackingId"))
        .thenReturn(container);
    when(containerTransformer.transformList(Collections.singletonList(container)))
        .thenReturn(Collections.singletonList(new ContainerDTO()));
    when(outboxConfig.getKafkaPublisherPolicySorter()).thenReturn("kafkaPublisherPolicySorter");
    when(outboxConfig.getKafkaPublisherPolicyEIDCPickEvent())
        .thenReturn("kafkaPublisherPolicyEIDCPickEvent");
    when(outboxConfig.getKafkaPublisherPolicyWFT()).thenReturn("kafkaPublisherPolicyWFT");
    when(outboxConfig.getKafkaPublisherPolicyEIDCReceiveEvent())
        .thenReturn("kafkaPublisherPolicyEIDCReceiveEvent");
    Collection<OutboxEvent> outboxEvents =
        rdcReceivingUtils.automationBuildOutboxEvents(
            receivedContainers, headers, instruction, deliveryDocument, Boolean.TRUE);
    assertNotNull(outboxEvents);
    assertEquals(4, outboxEvents.size());
    verify(containerPersisterService).getConsolidatedContainerForPublish("trackingId");
    verify(containerTransformer).transformList(Collections.singletonList(container));
    verifyNoMoreInteractions(containerPersisterService, containerTransformer);
  }

  @Test
  public void isPilotDeliveryItemEnabledForAutomationHappyPathTest() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    List<String> vad = new ArrayList<>();
    vad.add("814289");
    vad.add("876543");
    when(rdcManagedConfig.getValidAutomationDeliveries()).thenReturn(vad);
    List<String> vai = new ArrayList<>();
    vai.add("12345");
    vai.add("876543");
    when(rdcManagedConfig.getValidAutomationItems()).thenReturn(vai);
    boolean pilotDeliveryItemEnabledForAutomation =
        rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(814289l, 12345l);
    assertTrue(pilotDeliveryItemEnabledForAutomation);
  }

  @Test
  public void isPilotDeliveryItemEnabledForAutomationWithNullValuesTest() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    List<String> vad = new ArrayList<>();
    vad.add("814289");
    vad.add("876543");
    when(rdcManagedConfig.getValidAutomationDeliveries()).thenReturn(vad);
    List<String> vai = new ArrayList<>();
    vai.add("12345");
    vai.add("876543");
    when(rdcManagedConfig.getValidAutomationItems()).thenReturn(vai);
    boolean pilotDeliveryItemEnabledForAutomation =
        rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(null, null);
    assertFalse(pilotDeliveryItemEnabledForAutomation);
  }

  @Test
  public void isPilotDeliveryItemEnabledForAutomationWithItemNumberNullValueTest() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    List<String> vad = new ArrayList<>();
    vad.add("814289");
    vad.add("876543");
    when(rdcManagedConfig.getValidAutomationDeliveries()).thenReturn(vad);
    List<String> vai = new ArrayList<>();
    vai.add("12345");
    vai.add("876543");
    when(rdcManagedConfig.getValidAutomationItems()).thenReturn(vai);
    boolean pilotDeliveryItemEnabledForAutomation =
        rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(12345L, null);
    assertFalse(pilotDeliveryItemEnabledForAutomation);
  }

  @Test
  public void isPilotDeliveryItemEnabledForAutomationWithDeliveryNumberNullValueTest() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    List<String> vad = new ArrayList<>();
    vad.add("814289");
    vad.add("876543");
    when(rdcManagedConfig.getValidAutomationDeliveries()).thenReturn(vad);
    List<String> vai = new ArrayList<>();
    vai.add("12345");
    vai.add("876543");
    when(rdcManagedConfig.getValidAutomationItems()).thenReturn(vai);
    boolean pilotDeliveryItemEnabledForAutomation =
        rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(null, 814289L);
    assertFalse(pilotDeliveryItemEnabledForAutomation);
  }

  @Test
  public void isPilotDeliveryItemEnabledForAutomationWithDiffItemTest() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    List<String> vad = new ArrayList<>();
    vad.add("814289");
    vad.add("876543");
    when(rdcManagedConfig.getValidAutomationDeliveries()).thenReturn(vad);
    List<String> vai = new ArrayList<>();
    vai.add("123452");
    vai.add("876543");
    when(rdcManagedConfig.getValidAutomationItems()).thenReturn(vai);
    boolean pilotDeliveryItemEnabledForAutomation =
        rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(814289l, 12345l);
    assertFalse(pilotDeliveryItemEnabledForAutomation);
  }

  @Test
  public void isPilotDeliveryItemEnabledForAutomationWithoutValidAutomationDeliveriesTest() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    List<String> vad = new ArrayList<>();
    when(rdcManagedConfig.getValidAutomationDeliveries()).thenReturn(vad);
    List<String> vai = new ArrayList<>();
    vai.add("814289");
    vai.add("876543");
    when(rdcManagedConfig.getValidAutomationItems()).thenReturn(vai);
    boolean pilotDeliveryItemEnabledForAutomation =
        rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(814289l, 12345l);
    assertTrue(pilotDeliveryItemEnabledForAutomation);
  }

  @Test
  public void isPilotDeliveryItemEnabledForAutomationWithValidAutomationDeliveriesAsNullTest() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(rdcManagedConfig.getValidAutomationDeliveries()).thenReturn(null);
    List<String> vai = new ArrayList<>();
    vai.add("814289");
    vai.add("876543");
    when(rdcManagedConfig.getValidAutomationItems()).thenReturn(vai);
    boolean pilotDeliveryItemEnabledForAutomation =
        rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(814289l, 12345l);
    assertTrue(pilotDeliveryItemEnabledForAutomation);
  }

  @Test
  public void isPilotDeliveryItemEnabledForAutomationWithoutValidAutomationItemsTest() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    List<String> vad = new ArrayList<>();
    vad.add("814289");
    vad.add("876543");
    when(rdcManagedConfig.getValidAutomationDeliveries()).thenReturn(vad);
    List<String> vai = new ArrayList<>();
    when(rdcManagedConfig.getValidAutomationItems()).thenReturn(vai);
    boolean pilotDeliveryItemEnabledForAutomation =
        rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(814289l, 12345l);
    assertTrue(pilotDeliveryItemEnabledForAutomation);
  }

  @Test
  public void isPilotDeliveryItemEnabledForAutomationWithValidAutomationItemsAsNullTest() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    List<String> vad = new ArrayList<>();
    vad.add("814289");
    vad.add("876543");
    when(rdcManagedConfig.getValidAutomationDeliveries()).thenReturn(vad);
    List<String> vai = new ArrayList<>();
    when(rdcManagedConfig.getValidAutomationItems()).thenReturn(null);
    boolean pilotDeliveryItemEnabledForAutomation =
        rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(814289l, 12345l);
    assertTrue(pilotDeliveryItemEnabledForAutomation);
  }

  @Test
  public void isPilotDeliveryItemEnabledForAutomationWithWithDifferentDeliveryNumberTest() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    List<String> vad = new ArrayList<>();
    vad.add("8142891");
    vad.add("876543");
    when(rdcManagedConfig.getValidAutomationDeliveries()).thenReturn(vad);
    List<String> vai = new ArrayList<>();
    vai.add("814289");
    vai.add("12345");
    when(rdcManagedConfig.getValidAutomationItems()).thenReturn(null);
    boolean pilotDeliveryItemEnabledForAutomation =
        rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(814289l, 12345l);
    assertFalse(pilotDeliveryItemEnabledForAutomation);
  }

  @Test
  public void
      isPilotDeliveryItemEnabledForAutomationWithWithDifferentDeliveryNumberAndItemNumberTest() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    List<String> vad = new ArrayList<>();
    vad.add("8142891");
    vad.add("876543");
    when(rdcManagedConfig.getValidAutomationDeliveries()).thenReturn(vad);
    List<String> vai = new ArrayList<>();
    vai.add("814289");
    vai.add("876543");
    when(rdcManagedConfig.getValidAutomationItems()).thenReturn(null);
    boolean pilotDeliveryItemEnabledForAutomation =
        rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(814289l, 12345l);
    assertFalse(pilotDeliveryItemEnabledForAutomation);
  }

  @Test
  public void isPilotDeliveryItemEnabledForAutomationWithEmptyListsTest() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    List<String> vad = new ArrayList<>();
    when(rdcManagedConfig.getValidAutomationDeliveries()).thenReturn(vad);
    List<String> vai = new ArrayList<>();
    when(rdcManagedConfig.getValidAutomationItems()).thenReturn(vai);
    boolean pilotDeliveryItemEnabledForAutomation =
        rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(814289l, 12345l);
    assertTrue(pilotDeliveryItemEnabledForAutomation);
  }

  @Test
  public void isPilotDeliveryItemEnabledForAutomationWithNullValueTest() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    List<String> vad = new ArrayList<>();
    when(rdcManagedConfig.getValidAutomationDeliveries()).thenReturn(null);
    List<String> vai = new ArrayList<>();
    when(rdcManagedConfig.getValidAutomationItems()).thenReturn(null);
    boolean pilotDeliveryItemEnabledForAutomation =
        rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(814289l, 12345l);
    assertTrue(pilotDeliveryItemEnabledForAutomation);
  }

  @Test
  public void testPostReceivingUpdatesWithLessthanacase() throws Exception {
    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack().get(0);
    int receiveQty = 1;
    boolean isAtlasConvertedItem = true;
    List<ReceivedContainer> receivedContainers = getMockReceivedContainersWithSymStoreAlignment();

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_ENABLED_ON_KAFKA,
            false))
        .thenReturn(false);
    doNothing()
        .when(rdcContainerUtils)
        .postReceiptsToDcFin(any(Container.class), any(String.class));
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_KAFKA,
            false))
        .thenReturn(true);
    doNothing().when(kafkaAthenaPublisher).publishLabelToSorter(any(Container.class), anyString());

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_JMS,
            false))
        .thenReturn(true);
    doNothing().when(jmsSorterPublisher).publishLabelToSorter(any(Container.class), anyString());

    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(getMockContainerForSYMLabelType());
    doNothing().when(rdcContainerUtils).publishContainersToInventory(any(Container.class));

    doNothing()
        .when(symboticPutawayPublishHelper)
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    doNothing().when(rdcContainerUtils).publishContainerToEI(any(Container.class), any());

    rdcReceivingUtils.postReceivingUpdates(
        instruction,
        deliveryDocument,
        receiveQty,
        headers,
        isAtlasConvertedItem,
        receivedContainers,
        true);

    verify(rdcContainerUtils, times(0))
        .postReceiptsToDcFin(any(Container.class), any(String.class));
    verify(rdcContainerUtils, times(1)).publishContainersToInventory(any(Container.class));
    verify(symboticPutawayPublishHelper, times(1))
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false);
    verify(rdcContainerUtils, times(1)).publishContainerToEI(any(Container.class), any());
  }

  @Test
  public void populateInstructionFieldsWithActivityNameDANoncon() throws IOException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(true);
    deliveryDocumentLine.getAdditionalInfo().setHandlingCode("N");
    deliveryDocumentLine
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(RdcConstants.DA_BREAK_PACK_NON_CON_ITEM_HANDLING_CODE);
    Instruction instruction =
        rdcReceivingUtils.populateInstructionFields(
            instructionRequest, deliveryDocumentLine, headers, deliveryDocuments.get(0));
    assertNotNull(instruction);
    assertEquals(instruction.getActivityName(), WFTInstruction.NON_CON_CASEPACK.getActivityName());
  }

  @Test
  public void populateInstructionFieldsWithActivityNameDA() throws IOException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(true);
    deliveryDocumentLine.getAdditionalInfo().setHandlingCode("C");
    deliveryDocumentLine
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(RdcConstants.DA_BREAK_PACK_NON_CON_ITEM_HANDLING_CODE);
    Instruction instruction =
        rdcReceivingUtils.populateInstructionFields(
            instructionRequest, deliveryDocumentLine, headers, deliveryDocuments.get(0));
    assertNotNull(instruction);
    assertEquals(instruction.getActivityName(), WFTInstruction.DA.getActivityName());
  }

  @Test
  public void test_buildOutboxEvent() {

    OutboxEvent outboxEvent =
        rdcReceivingUtils.buildOutboxEvent(
            Collections.singletonMap("key", "value"),
            "body",
            "eventId",
            MetaData.with("meta", "value"),
            "policyId",
            Instant.now());

    assertNotNull(outboxEvent);
    assertNotNull(outboxEvent.getEventIdentifier());
    assertNotNull(outboxEvent.getMetaData());
    assertNotNull(outboxEvent.getPublisherPolicyId());
    assertNotNull(outboxEvent.getPayloadRef());
  }

  @Test
  public void test_buildOutboxEventsForAsyncFlow() throws ReceivingException {

    Container container = getMockContainerWithPickedStatus();
    container.setTrackingId("trackingId");
    when(containerPersisterService.getConsolidatedContainerForPublish(any())).thenReturn(container);
    when(outboxConfig.getKafkaPublisherPolicyInventory())
        .thenReturn("kafkaPublisherPolicyInventory");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    Collection<OutboxEvent> outboxEvents =
        rdcReceivingUtils.buildOutboxEventsForAsyncFlow(
            Collections.singletonList(getMockReceivedContainers()), headers);

    assertNotNull(outboxEvents);
  }

  @Test
  public void testGetConsolidatedContainerForPublish_Child_Limit() throws IOException {
    TenantContext.setFacilityNum(Integer.valueOf(32679));

    File resources = new ClassPathResource("MockContainers_DA_BP_WithMultipleChild.json").getFile();
    String originalContainer = new String(Files.readAllBytes(resources.toPath()));
    List<Container> containers = Arrays.asList(gson.fromJson(originalContainer, Container[].class));
    Container mockConsolidatedContainer = containers.get(0);
    // testing 11 child containers with limit of 5
    List<Container> parentContainers =
        rdcReceivingUtils.splitChildContainersInBatch(
            mockConsolidatedContainer,
            containers.get(0).getChildContainers(),
            "c326790000100000025655807",
            5);
    assertEquals(parentContainers.size(), 3);

    // testing 11 child containers with limit of 4
    parentContainers =
        rdcReceivingUtils.splitChildContainersInBatch(
            mockConsolidatedContainer,
            containers.get(0).getChildContainers(),
            "c326790000100000025655807",
            4);
    assertEquals(parentContainers.size(), 3);

    // testing 11 child containers with limit of 3
    parentContainers =
        rdcReceivingUtils.splitChildContainersInBatch(
            mockConsolidatedContainer,
            containers.get(0).getChildContainers(),
            "c326790000100000025655807",
            3);
    assertEquals(parentContainers.size(), 4);

    // testing 11 child containers with limit of 11
    parentContainers =
        rdcReceivingUtils.splitChildContainersInBatch(
            mockConsolidatedContainer,
            containers.get(0).getChildContainers(),
            "c326790000100000025655807",
            11);
    assertEquals(parentContainers.size(), 1);
  }

  @Test
  public void testGetConsolidatedContainerForPublish_Child_Limit_NoConsolidatedContainer()
      throws IOException {
    TenantContext.setFacilityNum(Integer.valueOf(32679));

    File resources = new ClassPathResource("MockContainers_DA_BP_WithMultipleChild.json").getFile();
    String originalContainer = new String(Files.readAllBytes(resources.toPath()));
    List<Container> containers = Arrays.asList(gson.fromJson(originalContainer, Container[].class));

    when(containerPersisterService.getContainerDetails(anyString())).thenReturn(containers.get(0));

    when(containerRepository.findAllByParentTrackingId(anyString()))
        .thenReturn(containers.get(0).getChildContainers());

    doReturn(getContainerItems()).when(containerItemRepository).findByTrackingIdIn(any());

    assertThrows(
        ReceivingBadDataException.class,
        () -> {
          rdcReceivingUtils.splitChildContainersInBatch(
              null, containers.get(0).getChildContainers(), "c326790000100000025655807", 5);
        });
  }

  public static List<ContainerItem> getContainerItems() {

    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem1 = new ContainerItem();
    containerItem1.setTrackingId("007590132679200108");
    containerItem1.setPurchaseReferenceNumber("34734743");
    containerItem1.setPurchaseReferenceLineNumber(1);
    containerItem1.setInboundChannelMethod("CROSSU");
    containerItem1.setVnpkQty(24);
    containerItem1.setWhpkQty(6);
    containerItem1.setItemNumber(100000L);
    containerItem1.setQuantity(24);
    containerItem1.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem1.setGtin("7437838348");
    containerItem1.setDescription("Dummy desc item1");
    containerItems.add(containerItem1);

    ContainerItem containerItem2 = new ContainerItem();
    containerItem2.setTrackingId("007590132679200108");
    containerItem2.setPurchaseReferenceNumber("34734743");
    containerItem2.setPurchaseReferenceLineNumber(1);
    containerItem2.setInboundChannelMethod("CROSSU");
    containerItem2.setVnpkQty(24);
    containerItem2.setWhpkQty(6);
    containerItem2.setItemNumber(100000L);
    containerItem2.setQuantity(24);
    containerItem2.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem2.setGtin("7437838349");
    containerItem2.setDescription("Dummy desc item2");
    containerItems.add(containerItem2);

    ContainerItem containerItem3 = new ContainerItem();
    containerItem3.setTrackingId("001100132679202030");
    containerItem3.setPurchaseReferenceNumber("34734743");
    containerItem3.setPurchaseReferenceLineNumber(1);
    containerItem3.setInboundChannelMethod("CROSSU");
    containerItem3.setVnpkQty(24);
    containerItem3.setWhpkQty(6);
    containerItem3.setItemNumber(100000L);
    containerItem3.setQuantity(24);
    containerItem3.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem3.setGtin("7437838349");
    containerItem3.setDescription("Dummy desc item3");
    containerItems.add(containerItem3);

    ContainerItem containerItem4 = new ContainerItem();
    containerItem4.setTrackingId("001100132679202030");
    containerItem4.setPurchaseReferenceNumber("34734743");
    containerItem4.setPurchaseReferenceLineNumber(1);
    containerItem4.setInboundChannelMethod("CROSSU");
    containerItem4.setVnpkQty(24);
    containerItem4.setWhpkQty(6);
    containerItem4.setItemNumber(100000L);
    containerItem4.setQuantity(24);
    containerItem4.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem4.setGtin("7437838349");
    containerItem4.setDescription("Dummy desc item4");
    containerItems.add(containerItem4);

    ContainerItem containerItem5 = new ContainerItem();
    containerItem5.setTrackingId("001100132679202033");
    containerItem5.setPurchaseReferenceNumber("34734743");
    containerItem5.setPurchaseReferenceLineNumber(1);
    containerItem5.setInboundChannelMethod("CROSSU");
    containerItem5.setVnpkQty(24);
    containerItem5.setWhpkQty(6);
    containerItem5.setItemNumber(100000L);
    containerItem5.setQuantity(24);
    containerItem5.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem5.setGtin("7437838349");
    containerItem5.setDescription("Dummy desc item5");
    containerItems.add(containerItem5);

    ContainerItem containerItem6 = new ContainerItem();
    containerItem6.setTrackingId("001100132679202033");
    containerItem6.setPurchaseReferenceNumber("34734743");
    containerItem6.setPurchaseReferenceLineNumber(1);
    containerItem6.setInboundChannelMethod("CROSSU");
    containerItem6.setVnpkQty(24);
    containerItem6.setWhpkQty(6);
    containerItem6.setItemNumber(100000L);
    containerItem6.setQuantity(24);
    containerItem6.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem6.setGtin("7437838349");
    containerItem6.setDescription("Dummy desc item6");
    containerItems.add(containerItem6);

    ContainerItem containerItem7 = new ContainerItem();
    containerItem7.setTrackingId("001100132679202041");
    containerItem7.setPurchaseReferenceNumber("34734743");
    containerItem7.setPurchaseReferenceLineNumber(1);
    containerItem7.setInboundChannelMethod("CROSSU");
    containerItem7.setVnpkQty(24);
    containerItem7.setWhpkQty(6);
    containerItem7.setItemNumber(100000L);
    containerItem7.setQuantity(24);
    containerItem7.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem7.setGtin("7437838349");
    containerItem7.setDescription("Dummy desc item7");
    containerItems.add(containerItem7);

    ContainerItem containerItem8 = new ContainerItem();
    containerItem8.setTrackingId("001100132679202041");
    containerItem8.setPurchaseReferenceNumber("34734743");
    containerItem8.setPurchaseReferenceLineNumber(1);
    containerItem8.setInboundChannelMethod("CROSSU");
    containerItem8.setVnpkQty(24);
    containerItem8.setWhpkQty(6);
    containerItem8.setItemNumber(100000L);
    containerItem8.setQuantity(24);
    containerItem8.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem8.setGtin("7437838349");
    containerItem8.setDescription("Dummy desc item8");
    containerItems.add(containerItem8);

    ContainerItem containerItem9 = new ContainerItem();
    containerItem9.setTrackingId("001100132679202028");
    containerItem9.setPurchaseReferenceNumber("34734743");
    containerItem9.setPurchaseReferenceLineNumber(1);
    containerItem9.setInboundChannelMethod("CROSSU");
    containerItem9.setVnpkQty(24);
    containerItem9.setWhpkQty(6);
    containerItem9.setItemNumber(100000L);
    containerItem9.setQuantity(24);
    containerItem9.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem9.setGtin("7437838349");
    containerItem9.setDescription("Dummy desc item9");
    containerItems.add(containerItem9);

    ContainerItem containerItem10 = new ContainerItem();
    containerItem10.setTrackingId("001100132679202032");
    containerItem10.setPurchaseReferenceNumber("34734743");
    containerItem10.setPurchaseReferenceLineNumber(1);
    containerItem10.setInboundChannelMethod("CROSSU");
    containerItem10.setVnpkQty(24);
    containerItem10.setWhpkQty(6);
    containerItem10.setItemNumber(100000L);
    containerItem10.setQuantity(24);
    containerItem10.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem10.setGtin("7437838349");
    containerItem10.setDescription("Dummy desc item10");
    containerItems.add(containerItem10);

    return containerItems;
  }

  @Test
  public void testCheckIfInstructionForRtsPut_BreakPackNonConRtsPutAtlasItem_Success()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForRtsPut();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(Boolean.TRUE);

    rdcReceivingUtils.validateRtsPutItems(
        deliveryDocuments.get(0),
        MockInstructionRequest.getInstructionRequestForRtsPut(),
        instructionResponse,
        headers);
    assertNotNull(instructionResponse.getInstruction());
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.NON_CON_RTS_PUT_RECEIVING.getInstructionCode());
    assertEquals(
        instructionResponse.getInstruction().getInstructionMsg(),
        RdcInstructionType.NON_CON_RTS_PUT_RECEIVING.getInstructionMsg());
  }

  @Test
  public void testBlockRdsReceivingForNonAtlasItem() throws Exception {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_RDS_RECEIVING_BLOCKED,
            false))
        .thenReturn(true);
    try {
      rdcReceivingUtils.blockRdsReceivingForNonAtlasItem(deliveryDocumentLine);
    } catch (Exception exception) {
      assertTrue(exception instanceof ReceivingBadDataException);
    }
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_RDS_RECEIVING_BLOCKED,
            false);
  }

  @Test
  public void testBlockRdsReceivingForNonAtlasItemWithRdsReceivingNotBlocked() throws Exception {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_RDS_RECEIVING_BLOCKED,
            false))
        .thenReturn(false);
    rdcReceivingUtils.blockRdsReceivingForNonAtlasItem(deliveryDocumentLine);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_RDS_RECEIVING_BLOCKED,
            false);
  }

  @Test
  public void testGetContainersCountToBeReceived_BreakPack_Voice_Pick_AtlasDaItems()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(true);
    deliveryDocumentLine
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(
            RdcConstants.DA_BREAK_PACK_NON_CON_VOICE_PICK_ITEM_HANDLING_CODE);
    int containerCount =
        rdcReceivingUtils.getContainersCountToBeReceived(deliveryDocumentLine, 1, null, false);
    assertEquals(containerCount, 2);
  }

  @Test
  public void testGetContainersCountToBeReceived_BreakPack_Voice_Pick_AtlasDaItemsForSlotting()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(true);
    deliveryDocumentLine
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(
            RdcConstants.DA_BREAK_PACK_NON_CON_VOICE_PICK_ITEM_HANDLING_CODE);
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    SlotDetails slotDetails = new SlotDetails();
    slotDetails.setMaxPallet(2);
    slotDetails.setStockType("N");
    slotDetails.setSlotSize(72);
    slotDetails.setCrossReferenceDoor("000");
    receiveInstructionRequest.setSlotDetails(slotDetails);
    int containerCount =
        rdcReceivingUtils.getContainersCountToBeReceived(
            deliveryDocumentLine, 2, receiveInstructionRequest, false);
    assertEquals(containerCount, 4);
  }

  @Test
  public void testOverridePackTypeCodeForBreakPackItem_AtlasConvertedItem_CasePack()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setVendorPack(12);
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setWarehousePack(12);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    rdcReceivingUtils.overridePackTypeCodeForBreakPackItem(
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0));

    assertEquals(
        "C",
        deliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getPackTypeCode());
    assertEquals(
        "C",
        deliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getHandlingCode());
  }

  @Test
  public void testOverridePackTypeCodeForBreakPackItem_AtlasConvertedItem_BreakPack()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setVendorPack(12);
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setWarehousePack(3);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    rdcReceivingUtils.overridePackTypeCodeForBreakPackItem(
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0));

    assertEquals(
        "B",
        deliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getPackTypeCode());

    assertEquals(
        "C",
        deliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getHandlingCode());
  }

  @Test
  public void testGetLabelFormat_legacy() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(false);
    LabelFormat labelFormat = rdcReceivingUtils.getLabelFormatForPallet(deliveryDocumentLine);
    assertEquals(labelFormat, LabelFormat.LEGACY_SSTK);
  }

  @Test
  public void testGetLabelFormat_atlas_rdc_pallet_enabled() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    LabelFormat labelFormat = rdcReceivingUtils.getLabelFormatForPallet(deliveryDocumentLine);
    assertEquals(labelFormat, LabelFormat.ATLAS_RDC_PALLET);
  }

  @Test
  public void testIsNGRServicesEnabled() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_NGR_SERVICES_DISABLED,
            false))
        .thenReturn(false);
    boolean isNGRServicesEnabled = rdcReceivingUtils.isNGRServicesEnabled();
    assertTrue(isNGRServicesEnabled);
  }

  @Test
  public void testIsNGRServicesDisabled() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_NGR_SERVICES_DISABLED,
            false))
        .thenReturn(true);
    boolean isNGRServicesEnabled = rdcReceivingUtils.isNGRServicesEnabled();
    assertFalse(isNGRServicesEnabled);
  }
}
