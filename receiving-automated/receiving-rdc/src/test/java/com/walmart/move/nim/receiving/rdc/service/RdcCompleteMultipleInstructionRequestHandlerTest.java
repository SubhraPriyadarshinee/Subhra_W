package com.walmart.move.nim.receiving.rdc.service;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.client.nimrds.model.*;
import com.walmart.move.nim.receiving.core.client.slotting.SlottingRestApiClient;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.validators.InstructionStateValidator;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.PrintJob;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingDivertLocations;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletRequest;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponseWithRdsResponse;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.PrintJobService;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.label.LabelFormat;
import com.walmart.move.nim.receiving.rdc.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.rdc.mock.data.MockRdcInstruction;
import com.walmart.move.nim.receiving.rdc.utils.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.IOException;
import java.util.*;
import org.mockito.*;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RdcCompleteMultipleInstructionRequestHandlerTest {

  @Mock private AppConfig appconfig;
  @Mock private RdcManagedConfig rdcManagedConfig;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Spy private RdcInstructionUtils rdcInstructionUtils = new RdcInstructionUtils();
  @Mock private RdcContainerUtils rdcContainerUtils;
  @Mock private NimRdsService nimRdsService;
  @Mock private InstructionStateValidator instructionStateValidator;
  @Mock private PrintJobService printJobService;
  @Mock private ReceiptPublisher receiptPublisher;
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private InstructionHelperService instructionHelperService;
  @Mock private ContainerService containerService;
  @Mock private RdcInstructionHelper rdcInstructionHelper;
  @Mock private SlottingRestApiClient slottingRestApiClient;
  @Mock private RdcLpnUtils rdcLpnUtils;
  @Mock private RdcDcFinUtils rdcDcFinUtils;
  @Mock private MovePublisher movePublisher;
  @Mock private Transformer<Container, ContainerDTO> transformer;
  @Captor private ArgumentCaptor<List<ContainerDTO>> receiptsArgumentCaptor;
  @Mock private RdcReceivingUtils rdcReceivingUtils;

  private Gson gson = new Gson();
  private final String countryCode = "US";
  private final String facilityNum = "32818";

  @InjectMocks
  private RdcCompleteMultipleInstructionRequestHandler rdcCompleteMultipleInstructionRequestHandler;

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(rdcCompleteMultipleInstructionRequestHandler, "gson", gson);
    ReflectionTestUtils.setField(rdcCompleteMultipleInstructionRequestHandler, "gson", gson);
    ReflectionTestUtils.setField(rdcInstructionUtils, "rdcManagedConfig", rdcManagedConfig);
    ReflectionTestUtils.setField(rdcInstructionUtils, "rdcContainerUtils", rdcContainerUtils);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
  }

  @AfterMethod
  public void cleanup() {
    reset(
        appconfig,
        rdcManagedConfig,
        tenantSpecificConfigReader,
        rdcInstructionUtils,
        rdcContainerUtils,
        nimRdsService,
        instructionStateValidator,
        printJobService,
        receiptPublisher,
        instructionPersisterService,
        instructionHelperService,
        containerService,
        slottingRestApiClient,
        rdcDcFinUtils,
        movePublisher,
        transformer,
        rdcLpnUtils);
  }

  private CompleteMultipleInstructionData getCompleteMultipleInstructionData(long instructionId) {
    CompleteMultipleInstructionData mockCompleteMultipleInstructionData =
        new CompleteMultipleInstructionData();
    mockCompleteMultipleInstructionData.setInstructionId(instructionId);

    SlotDetails mockSlotDetails = new SlotDetails();
    mockSlotDetails.setSlot("A120");
    mockCompleteMultipleInstructionData.setSlotDetails(mockSlotDetails);

    return mockCompleteMultipleInstructionData;
  }

  private ReceivedContainer getReceivedContainer(String po, int poLine, String trackingId) {
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setPoNumber(po);
    receivedContainer.setPoLine(poLine);
    receivedContainer.setLabelTrackingId(trackingId);
    receivedContainer.setReceiver(12);

    Destination mockDestination = new Destination();
    mockDestination.setSlot("A120");
    mockDestination.setSlot_size(72);

    receivedContainer.setDestinations(Arrays.asList(mockDestination));

    return receivedContainer;
  }

  @Test
  public void testCompleteMultipleInstructionsHappyPathForNonAtlasItem() throws ReceivingException {
    when(appconfig.isWftPublishEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getDCTimeZone(TenantContext.getFacilityNum()))
        .thenReturn("UTC");

    ReceiveContainersResponseBody mockReceiveContainersResponseBody =
        new ReceiveContainersResponseBody();
    List<ReceivedContainer> receivedContainerList = new ArrayList<>();
    receivedContainerList.add(getReceivedContainer("4166030001", 1, "1"));
    receivedContainerList.add(getReceivedContainer("4166030001", 2, "2"));
    receivedContainerList.add(getReceivedContainer("4166030001", 3, "3"));
    mockReceiveContainersResponseBody.setReceived(receivedContainerList);

    doReturn(mockReceiveContainersResponseBody)
        .when(nimRdsService)
        .getMultipleContainerLabelsFromRds(anyMap(), any(HttpHeaders.class));
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    doAnswer(
            (Answer<Instruction>)
                invocation -> {
                  Long instructionId = (Long) invocation.getArguments()[0];
                  Instruction instruction = MockRdcInstruction.getInstruction();
                  instruction.setPurchaseReferenceLineNumber(instructionId.intValue());
                  instruction.setId(instructionId);
                  instruction.setCreateUserId("sysadmin");
                  instruction.setCreateTs(new Date());
                  return instruction;
                })
        .when(instructionPersisterService)
        .getInstructionById(anyLong());
    doAnswer(
            (Answer<List>)
                invocation -> {
                  Long instructionId = (Long) invocation.getArguments()[0];
                  Container mockContainer = new Container();
                  mockContainer.setInstructionId(instructionId);

                  mockContainer.setContainerItems(Arrays.asList(new ContainerItem()));

                  return Arrays.asList(mockContainer);
                })
        .when(containerService)
        .getContainerByInstruction(anyLong());

    PrintJob mockPrintJob = new PrintJob();
    mockPrintJob.setId(9l);
    doReturn(mockPrintJob)
        .when(printJobService)
        .createPrintJob(anyLong(), anyLong(), any(Set.class), anyString());
    doNothing()
        .when(rdcInstructionHelper)
        .persistForCompleteInstruction(anyList(), anyList(), anyList());
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    when(rdcContainerUtils.getContainerDetails(
            anyString(), anyMap(), any(ContainerType.class), anyString()))
        .thenReturn(getMockContainerDetails());
    when(rdcInstructionUtils.isAtlasConvertedInstruction(any(Instruction.class))).thenReturn(false);
    doCallRealMethod()
        .when(rdcReceivingUtils)
        .getLabelFormatForPallet(any(DeliveryDocumentLine.class));
    BulkCompleteInstructionRequest mockBulkCompleteInstructionRequest =
        new BulkCompleteInstructionRequest();
    List<CompleteMultipleInstructionData> instructionData = new ArrayList<>();
    instructionData.add(getCompleteMultipleInstructionData(1));
    instructionData.add(getCompleteMultipleInstructionData(2));
    instructionData.add(getCompleteMultipleInstructionData(3));
    mockBulkCompleteInstructionRequest.setInstructionData(instructionData);

    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    mockHttpHeaders.add(RdcConstants.WFT_LOCATION_ID, "23");
    mockHttpHeaders.add(RdcConstants.WFT_LOCATION_TYPE, "DOOR-23");
    mockHttpHeaders.add(RdcConstants.WFT_SCC_CODE, "0086623");

    CompleteMultipleInstructionResponse result =
        rdcCompleteMultipleInstructionRequestHandler.complete(
            mockBulkCompleteInstructionRequest, mockHttpHeaders);
    assertNotNull(result);

    verify(instructionPersisterService, times(3)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(3)).validate(any(Instruction.class));
    verify(nimRdsService, times(1))
        .getMultipleContainerLabelsFromRds(anyMap(), any(HttpHeaders.class));
    verify(containerService, times(3)).getContainerByInstruction(anyLong());
    verify(instructionHelperService, times(3))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    verify(rdcContainerUtils, times(3))
        .getContainerDetails(anyString(), anyMap(), any(ContainerType.class), anyString());
    verify(movePublisher, times(0))
        .publishMove(anyList(), any(LinkedTreeMap.class), any(HttpHeaders.class));
  }

  @Test
  public void
      testCompleteMultipleInstructionsHappyPathForNonAtlasItemSmartSlottingIntegrationEnabled()
          throws Exception {
    when(appconfig.isWftPublishEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getDCTimeZone(TenantContext.getFacilityNum()))
        .thenReturn("UTC");

    String slottingResponse =
        ReceivingUtils.readClassPathResourceAsString(
            "SlottingResponseWithRdsContainersForSplitPallet.json");
    SlottingPalletResponseWithRdsResponse slottingPalletResponseWithRdsResponse =
        gson.fromJson(slottingResponse, SlottingPalletResponseWithRdsResponse.class);
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    doAnswer(
            (Answer<Instruction>)
                invocation -> {
                  Long instructionId = (Long) invocation.getArguments()[0];
                  Instruction instruction = MockRdcInstruction.getInstruction();
                  instruction.setPurchaseReferenceLineNumber(instructionId.intValue());
                  instruction.setId(instructionId);
                  instruction.setCreateUserId("sysadmin");
                  instruction.setCreateTs(new Date());
                  return instruction;
                })
        .when(instructionPersisterService)
        .getInstructionById(anyLong());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(nimRdsService.getReceiveContainersRequestBody(anyMap(), anyString()))
        .thenReturn(getMockReceiveContainersRequestBodyForSplitPallet());
    doAnswer(
            (Answer<List>)
                invocation -> {
                  Long instructionId = (Long) invocation.getArguments()[0];
                  Container mockContainer = new Container();
                  mockContainer.setInstructionId(instructionId);

                  mockContainer.setContainerItems(Arrays.asList(new ContainerItem()));

                  return Arrays.asList(mockContainer);
                })
        .when(containerService)
        .getContainerByInstruction(anyLong());

    PrintJob mockPrintJob = new PrintJob();
    mockPrintJob.setId(9l);
    doReturn(mockPrintJob)
        .when(printJobService)
        .createPrintJob(anyLong(), anyLong(), any(Set.class), anyString());
    doNothing()
        .when(rdcInstructionHelper)
        .persistForCompleteInstruction(anyList(), anyList(), anyList());
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    when(slottingRestApiClient.getSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class)))
        .thenReturn(slottingPalletResponseWithRdsResponse);
    when(rdcContainerUtils.getContainerDetails(
            anyString(), anyMap(), any(ContainerType.class), anyString()))
        .thenReturn(getMockContainerDetails());
    when(rdcInstructionUtils.isAtlasConvertedInstruction(any(Instruction.class))).thenReturn(false);
    doCallRealMethod()
        .when(rdcReceivingUtils)
        .getLabelFormatForPallet(any(DeliveryDocumentLine.class));

    BulkCompleteInstructionRequest mockBulkCompleteInstructionRequest =
        new BulkCompleteInstructionRequest();
    List<CompleteMultipleInstructionData> instructionData = new ArrayList<>();
    instructionData.add(getCompleteMultipleInstructionData(1));
    instructionData.add(getCompleteMultipleInstructionData(2));
    instructionData.add(getCompleteMultipleInstructionData(3));
    mockBulkCompleteInstructionRequest.setInstructionData(instructionData);

    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    mockHttpHeaders.add(RdcConstants.WFT_LOCATION_ID, "23");
    mockHttpHeaders.add(RdcConstants.WFT_LOCATION_TYPE, "DOOR-23");
    mockHttpHeaders.add(RdcConstants.WFT_SCC_CODE, "0086623");

    CompleteMultipleInstructionResponse result =
        rdcCompleteMultipleInstructionRequestHandler.complete(
            mockBulkCompleteInstructionRequest, mockHttpHeaders);
    assertNotNull(result);

    verify(instructionPersisterService, times(3)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(3)).validate(any(Instruction.class));
    verify(nimRdsService, times(0))
        .getMultipleContainerLabelsFromRds(anyMap(), any(HttpHeaders.class));
    verify(containerService, times(3)).getContainerByInstruction(anyLong());
    verify(instructionHelperService, times(3))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    verify(rdcContainerUtils, times(3))
        .getContainerDetails(anyString(), anyMap(), any(ContainerType.class), anyString());
    verify(movePublisher, times(0))
        .publishMove(anyList(), any(LinkedTreeMap.class), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      testCompleteMultipleInstructionThrowsErrorFromRDSForNonAtlasItemSmartSlottingIntegrationEnabled()
          throws Exception {
    when(appconfig.isWftPublishEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getDCTimeZone(TenantContext.getFacilityNum()))
        .thenReturn("UTC");

    String slottingResponse =
        ReceivingUtils.readClassPathResourceAsString(
            "SlottingResponseWithRdsContainersForSplitPallet.json");
    SlottingPalletResponseWithRdsResponse slottingPalletResponseWithRdsResponse =
        gson.fromJson(slottingResponse, SlottingPalletResponseWithRdsResponse.class);
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    doAnswer(
            (Answer<Instruction>)
                invocation -> {
                  Long instructionId = (Long) invocation.getArguments()[0];
                  Instruction instruction = MockRdcInstruction.getInstruction();
                  instruction.setPurchaseReferenceLineNumber(instructionId.intValue());
                  instruction.setId(instructionId);
                  instruction.setCreateUserId("sysadmin");
                  instruction.setCreateTs(new Date());
                  return instruction;
                })
        .when(instructionPersisterService)
        .getInstructionById(anyLong());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(nimRdsService.getReceiveContainersRequestBody(anyMap(), anyString()))
        .thenReturn(getMockReceiveContainersRequestBodyForSplitPallet());
    doAnswer(
            (Answer<List>)
                invocation -> {
                  Long instructionId = (Long) invocation.getArguments()[0];
                  Container mockContainer = new Container();
                  mockContainer.setInstructionId(instructionId);

                  mockContainer.setContainerItems(Arrays.asList(new ContainerItem()));

                  return Arrays.asList(mockContainer);
                })
        .when(containerService)
        .getContainerByInstruction(anyLong());

    PrintJob mockPrintJob = new PrintJob();
    mockPrintJob.setId(9l);
    doReturn(mockPrintJob)
        .when(printJobService)
        .createPrintJob(anyLong(), anyLong(), any(Set.class), anyString());
    doNothing()
        .when(rdcInstructionHelper)
        .persistForCompleteInstruction(anyList(), anyList(), anyList());
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.NIM_RDS_MULTI_LABEL_GENERIC_ERROR,
                ReceivingConstants.NIM_RDS_MULTI_LABEL_GENERIC_ERROR))
        .when(slottingRestApiClient)
        .getSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));
    when(rdcInstructionUtils.isAtlasConvertedInstruction(any(Instruction.class))).thenReturn(false);

    BulkCompleteInstructionRequest mockBulkCompleteInstructionRequest =
        new BulkCompleteInstructionRequest();
    List<CompleteMultipleInstructionData> instructionData = new ArrayList<>();
    instructionData.add(getCompleteMultipleInstructionData(1));
    instructionData.add(getCompleteMultipleInstructionData(2));
    instructionData.add(getCompleteMultipleInstructionData(3));
    mockBulkCompleteInstructionRequest.setInstructionData(instructionData);

    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    mockHttpHeaders.add(RdcConstants.WFT_LOCATION_ID, "23");
    mockHttpHeaders.add(RdcConstants.WFT_LOCATION_TYPE, "DOOR-23");
    mockHttpHeaders.add(RdcConstants.WFT_SCC_CODE, "0086623");

    rdcCompleteMultipleInstructionRequestHandler.complete(
        mockBulkCompleteInstructionRequest, mockHttpHeaders);

    verify(instructionPersisterService, times(3)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(3)).validate(any(Instruction.class));
    verify(nimRdsService, times(0))
        .getMultipleContainerLabelsFromRds(anyMap(), any(HttpHeaders.class));
  }

  @Test
  public void testCompleteMultipleInstructionsHappyPathForAtlasItem()
      throws ReceivingException, IOException {

    when(appconfig.isWftPublishEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.IS_MOVE_PUBLISH_ENABLED, false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getDCTimeZone(TenantContext.getFacilityNum()))
        .thenReturn("UTC");

    Instruction instruction = MockRdcInstruction.getInstruction();
    instruction.setDeliveryDocument(
        gson.toJson(MockDeliveryDocuments.getDeliveryDocumentsForSSTK_AtlasConvertedItem().get(0)));
    ReceiveContainersResponseBody mockReceiveContainersResponseBody =
        new ReceiveContainersResponseBody();
    List<ReceivedContainer> receivedContainerList = new ArrayList<>();
    receivedContainerList.add(getReceivedContainer("4166030001", 1, "1"));
    receivedContainerList.add(getReceivedContainer("4166030001", 2, "2"));
    receivedContainerList.add(getReceivedContainer("4166030001", 3, "3"));
    mockReceiveContainersResponseBody.setReceived(receivedContainerList);

    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    doAnswer(
            (Answer<Instruction>)
                invocation -> {
                  Long instructionId = (Long) invocation.getArguments()[0];
                  Instruction instruction1 = MockRdcInstruction.getInstruction();
                  List<DeliveryDocument> deliveryDocuments =
                      MockDeliveryDocuments.getDeliveryDocumentsForSSTK_AtlasConvertedItem();
                  deliveryDocuments
                      .get(0)
                      .getDeliveryDocumentLines()
                      .get(0)
                      .setItemNbr(Long.parseLong(instructionId + "8764363"));
                  instruction1.setDeliveryDocument(gson.toJson(deliveryDocuments.get(0)));
                  instruction1.setPurchaseReferenceLineNumber(instructionId.intValue());
                  instruction1.setId(instructionId);
                  instruction1.setReceivedQuantity(10);
                  instruction1.setCreateUserId("sysadmin");
                  instruction1.setCreateTs(new Date());
                  return instruction1;
                })
        .when(instructionPersisterService)
        .getInstructionById(anyLong());
    doAnswer(
            (Answer<List>)
                invocation -> {
                  Long instructionId = (Long) invocation.getArguments()[0];
                  Container mockContainer = new Container();
                  mockContainer.setLocation("123");
                  mockContainer.setInstructionId(instructionId);
                  Map<String, Object> containerMiscInfo = new HashMap<>();
                  containerMiscInfo.put(ReceivingConstants.PRO_DATE, "Apr 8, 2022 4:29:23 AM");
                  mockContainer.setContainerMiscInfo(containerMiscInfo);
                  mockContainer.setContainerItems(Arrays.asList(new ContainerItem()));

                  return Arrays.asList(mockContainer);
                })
        .when(containerService)
        .getContainerByInstruction(anyLong());
    doNothing()
        .when(movePublisher)
        .publishMove(anyList(), any(LinkedTreeMap.class), any(HttpHeaders.class));

    PrintJob mockPrintJob = new PrintJob();
    List<String> lpns =
        Arrays.asList(
            "a47263319186056300", "a47263319186056301", "a47263319186056302", "a47263319186056303");
    mockPrintJob.setId(9l);
    doReturn(mockPrintJob)
        .when(printJobService)
        .createPrintJob(anyLong(), anyLong(), any(Set.class), anyString());
    doNothing()
        .when(rdcInstructionHelper)
        .persistForCompleteInstruction(anyList(), anyList(), anyList());
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    when(transformer.transformList(anyList())).thenReturn(getMockContainerDTO());
    doNothing().when(containerService).publishMultipleContainersToInventory(anyList());
    when(rdcContainerUtils.getContainerDetails(
            anyString(), anyMap(), any(ContainerType.class), anyString()))
        .thenReturn(getMockContainerDetails());
    when(rdcLpnUtils.getLPNs(anyInt(), any(HttpHeaders.class))).thenReturn(lpns);
    when(slottingRestApiClient.getSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class)))
        .thenReturn(mockSlottingPalletResponse());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(rdcInstructionUtils.isAtlasConvertedInstruction(any(Instruction.class))).thenReturn(true);
    doNothing().when(rdcDcFinUtils).postToDCFin(anyList(), anyString());
    when(rdcReceivingUtils.getLabelFormatForPallet(any(DeliveryDocumentLine.class)))
        .thenReturn(LabelFormat.ATLAS_RDC_SSTK);

    BulkCompleteInstructionRequest mockBulkCompleteInstructionRequest =
        new BulkCompleteInstructionRequest();
    List<CompleteMultipleInstructionData> instructionData = new ArrayList<>();
    instructionData.add(getCompleteMultipleInstructionData(1));
    instructionData.add(getCompleteMultipleInstructionData(2));
    instructionData.add(getCompleteMultipleInstructionData(3));
    mockBulkCompleteInstructionRequest.setInstructionData(instructionData);

    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    mockHttpHeaders.add(RdcConstants.WFT_LOCATION_ID, "23");
    mockHttpHeaders.add(RdcConstants.WFT_LOCATION_TYPE, "DOOR-23");
    mockHttpHeaders.add(RdcConstants.WFT_SCC_CODE, "0086623");

    CompleteMultipleInstructionResponse result =
        rdcCompleteMultipleInstructionRequestHandler.complete(
            mockBulkCompleteInstructionRequest, mockHttpHeaders);
    assertNotNull(result);

    verify(instructionPersisterService, times(3)).getInstructionById(anyLong());
    verify(instructionStateValidator, times(3)).validate(any(Instruction.class));
    verify(containerService, times(3)).getContainerByInstruction(anyLong());
    verify(rdcContainerUtils, times(3))
        .getContainerDetails(anyString(), anyMap(), any(ContainerType.class), anyString());
    verify(rdcLpnUtils, times(1)).getLPNs(anyInt(), any(HttpHeaders.class));
    verify(slottingRestApiClient, times(1))
        .getSlot(any(SlottingPalletRequest.class), any(HttpHeaders.class));
    verify(instructionHelperService, times(3))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    verify(transformer, times(1)).transformList(anyList());
    verify(containerService, times(1))
        .publishMultipleContainersToInventory(receiptsArgumentCaptor.capture());
    assertTrue(receiptsArgumentCaptor.getValue().size() > 0);
    assertNotNull(
        receiptsArgumentCaptor.getValue().get(0).getContainerItems().get(0).getSlotType());
    assertNotNull(
        receiptsArgumentCaptor.getValue().get(0).getContainerItems().get(0).getAsrsAlignment());
    assertNotNull(
        receiptsArgumentCaptor.getValue().get(0).getDestination().get(ReceivingConstants.SLOT));
    verify(tenantSpecificConfigReader, times(4))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_INTEGRATION_ENABLED,
            false);
    verify(rdcDcFinUtils, times(1)).postToDCFin(anyList(), anyString());
    verify(movePublisher, times(1))
        .publishMove(anyList(), any(LinkedTreeMap.class), any(HttpHeaders.class));
  }

  private ContainerDetails getMockContainerDetails() {
    ContainerDetails containerDetails = new ContainerDetails();
    containerDetails.setContainerId("lpn123");
    containerDetails.setCtrType(ContainerType.PALLET.getText());
    containerDetails.setCtrReusable(false);
    containerDetails.setCtrShippable(false);
    containerDetails.setCtrLabel(new HashMap<>());
    return containerDetails;
  }

  private SlottingPalletResponse mockSlottingPalletResponse() {

    SlottingDivertLocations location = new SlottingDivertLocations();
    location.setType("success");
    location.setLocation("A1234");
    location.setItemNbr(579516308);
    location.setAsrsAlignment("SYM2");
    location.setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);
    List<SlottingDivertLocations> locationList = new ArrayList();
    locationList.add(location);

    SlottingPalletResponse mockSlottingResponseBody = new SlottingPalletResponse();
    mockSlottingResponseBody.setMessageId("a1-b1-c1");
    mockSlottingResponseBody.setLocations(locationList);
    return mockSlottingResponseBody;
  }

  private Container getMockContainerForAtlasItem() {
    Container container = new Container();
    container.setInstructionId(123L);
    container.setTrackingId("lpn123");
    container.setDeliveryNumber(123456L);
    container.setParentTrackingId(null);
    container.setInventoryStatus("AVAILABLE");
    container.setContainerItems(
        Collections.singletonList(getMockContainerItemForAtlasItem("SYM1")));
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.SLOT, "A0001");
    container.setDestination(destination);
    return container;
  }

  private ContainerItem getMockContainerItemForAtlasItem(String asrsAlignment) {
    ContainerItem containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("PO1");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(6);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);
    containerItem.setAsrsAlignment(asrsAlignment);
    containerItem.setRotateDate(new Date());
    containerItem.setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM, "true");
    containerItem.setContainerItemMiscInfo(containerItemMiscInfo);
    return containerItem;
  }

  private ReceiveContainersRequestBody getMockReceiveContainersRequestBodyForSplitPallet() {
    ReceiveContainersRequestBody receiveContainersRequestBody = new ReceiveContainersRequestBody();
    List<ContainerOrder> containerOrders = new ArrayList<>();
    ContainerOrder containerOrder1 = new ContainerOrder();
    containerOrder1.setContainerGroupId("333434");
    containerOrder1.setDoorNum("232");
    containerOrder1.setQty(344);
    containerOrders.add(containerOrder1);
    ContainerOrder containerOrder2 = new ContainerOrder();
    containerOrder2.setContainerGroupId("333434");
    containerOrder2.setDoorNum("123");
    containerOrder2.setQty(223);
    containerOrders.add(containerOrder2);
    receiveContainersRequestBody.setContainerOrders(containerOrders);
    return receiveContainersRequestBody;
  }

  private List<ContainerDTO> getMockContainerDTO() {
    Transformer<Container, ContainerDTO> transformer = new ContainerTransformer();
    List<Container> containers = Arrays.asList(getMockContainerForAtlasItem());
    return transformer.transformList(containers);
  }
}
