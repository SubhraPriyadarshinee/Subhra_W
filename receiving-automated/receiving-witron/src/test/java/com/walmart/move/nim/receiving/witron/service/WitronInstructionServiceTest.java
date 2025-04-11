package com.walmart.move.nim.receiving.witron.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.PO_POL_CANCELLED_ERROR;
import static com.walmart.move.nim.receiving.core.common.exception.InstructionError.HACCP_ITEM_ALERT;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.testng.Assert.*;
import static org.testng.Assert.assertEquals;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.client.slotting.SlottingPalletBuildResponse;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.GdmError;
import com.walmart.move.nim.receiving.core.common.exception.GdmErrorCode;
import com.walmart.move.nim.receiving.core.common.exception.InstructionErrorCode;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.validators.DeliveryValidator;
import com.walmart.move.nim.receiving.core.common.validators.InstructionStateValidator;
import com.walmart.move.nim.receiving.core.common.validators.PurchaseReferenceValidator;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.DeliveryItemOverride;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.builder.ContainerLabelBuilder;
import com.walmart.move.nim.receiving.witron.constants.GdcConstants;
import com.walmart.move.nim.receiving.witron.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.witron.model.ContainerLabel;
import com.walmart.move.nim.receiving.witron.model.PrintLabelRequest;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import org.mockito.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class WitronInstructionServiceTest extends ReceivingTestBase {

  private final Gson gson = new Gson();

  @Mock private GdcSlottingServiceImpl slottingService;

  @Mock private ReceiptService receiptService;

  @Mock private DeliveryStatusPublisher deliveryStatusPublisher;

  @Mock private TenantSpecificConfigReader configUtils;

  @Mock private InstructionPersisterService instructionPersisterService;

  @Mock private InstructionRepository instructionRepository;

  @Mock private InstructionHelperService instructionHelperService;

  @Mock private DeliveryItemOverrideService deliveryItemOverrideService;

  @Mock private LPNCacheService lpnCacheService;

  @Mock private ContainerLabelBuilder containerLabelBuilder;

  @Mock private JmsPublisher jmsPublisher;

  @InjectMocks private WitronInstructionService witronInstructionService;
  @InjectMocks private InstructionService instructionService;
  @Mock private WitronDeliveryMetaDataService witronDeliveryMetaDataService;
  @Mock private DeliveryService deliveryService;
  @Mock private ContainerService containerService;
  @Mock private ReceiptPublisher receiptPublisher;
  @Mock private PurchaseReferenceValidator purchaseReferenceValidator;
  @Mock private AppConfig appConfig;
  @Mock private RegulatedItemService regulatedItemService;
  @Mock private DeliveryDocumentHelper deliveryDocumentHelper;
  @Spy private InstructionStateValidator instructionStateValidator;

  @InjectMocks @Spy
  private DefaultDeliveryDocumentsSearchHandler defaultDeliveryDocumentsSearchHandler;

  @InjectMocks @Spy
  private VendorBasedDeliveryDocumentsSearchHandler vendorBasedDeliveryDocumentsSearchHandler;

  @Mock ASNReceivingAuditLogger asnReceivingAuditLogger;

  @Mock private WitronVendorValidator witronVendorValidator;
  @Mock private InstructionUtils instructionUtils;

  private final HttpHeaders httpHeaders = GdcHttpHeaders.getHeaders();

  private final String multiUserErrorMsg =
      InstructionErrorCode.getErrorValue(ReceivingException.MULTI_USER_ERROR).getErrorMessage();

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        witronInstructionService, WitronInstructionService.class, "gson", gson, Gson.class);
    ReflectionTestUtils.setField(
        witronInstructionService, InstructionService.class, "gson", gson, Gson.class);
    ReflectionTestUtils.setField(
        witronInstructionService,
        InstructionService.class,
        "deliveryValidator",
        new DeliveryValidator(),
        DeliveryValidator.class);
    ReflectionTestUtils.setField(
        instructionService, "purchaseReferenceValidator", purchaseReferenceValidator);
    ReflectionTestUtils.setField(
        instructionService, "purchaseReferenceValidator", purchaseReferenceValidator);
    ReflectionTestUtils.setField(instructionService, "regulatedItemService", regulatedItemService);
    ReflectionTestUtils.setField(defaultDeliveryDocumentsSearchHandler, "gson", gson);
    ReflectionTestUtils.setField(
        witronInstructionService,
        WitronInstructionService.class,
        "tenantSpecificConfigReader",
        configUtils,
        TenantSpecificConfigReader.class);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);
  }

  @AfterMethod
  public void tearDown() {
    reset(receiptService);
    reset(lpnCacheService);
    reset(slottingService);
    reset(jmsPublisher);
    reset(configUtils);
    reset(instructionPersisterService);
    reset(instructionHelperService);
    reset(deliveryItemOverrideService);
    reset(deliveryStatusPublisher);
    reset(containerLabelBuilder);
    reset(purchaseReferenceValidator);
    reset(containerService);
    reset(deliveryService);
    reset(witronDeliveryMetaDataService);
    reset(instructionRepository);
    reset(deliveryDocumentHelper);
  }

  @BeforeMethod
  public void setup() {
    when(instructionUtils.isLabelsMissingFlowManualOVFConv(any())).thenReturn(false);
    when(instructionUtils.isLabelsMissingFlowManualOVFConvGetAllocationsRequest(any()))
        .thenReturn(false);
  }

  @Test
  public void test_serveInstructionRequest() throws Exception {

    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("d1fca5ba-5f36-466d-ba2e-4fadbdd11c35");
    instructionRequest.setDeliveryNumber("9967271326");
    instructionRequest.setDeliveryStatus("ARV");
    instructionRequest.setUpcNumber("10681131135822");
    instructionRequest.setDoorNumber("V6949");

    File resource = new ClassPathResource("gdm_scanUpc_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    Optional<DeliveryItemOverride> optionalDeliveryItemOverride = Optional.empty();
    doReturn(mockResponse)
        .when(deliveryService)
        .findDeliveryDocument(9967271326l, "10681131135822", httpHeaders);
    doReturn("F67387000020022168").when(lpnCacheService).getLPNBasedOnTenant(httpHeaders);
    doReturn(true).when(configUtils).isDeliveryItemOverrideEnabled(anyInt());
    doReturn(optionalDeliveryItemOverride)
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());

    SlottingPalletBuildResponse slottingPalletBuildResponse =
        getMockSlottingResponse("abc123", "Ind-1");

    doReturn(slottingPalletBuildResponse)
        .when(slottingService)
        .acquireSlot(any(InstructionRequest.class), anyString(), anyString(), any());

    ContainerLabel containerLabel = createMockContainerLabel();
    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            "32612",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    doReturn(containerLabel)
        .when(containerLabelBuilder)
        .generateContainerLabel(anyString(), anyString(), any(DeliveryDocumentLine.class), any());

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());

    doReturn(Boolean.TRUE)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE);
    doReturn(new DeliveryMetaData())
        .when(witronDeliveryMetaDataService)
        .doManagerOverride(any(), any(), any(), any(), any());
    doReturn(new Pair<>(830, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetails(eq(instructionRequest.getProblemTagId()), any());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.PUBLISH_CONTAINER);
    when(appConfig.isCheckDeliveryStatusReceivable()).thenReturn(false);

    List<DeliveryDocument> deliveryDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    doReturn(deliveryDocuments).when(deliveryDocumentHelper).updateDeliveryDocuments(any());

    InstructionResponse serveInstructionResponse =
        witronInstructionService.serveInstructionRequest(
            gson.toJson(instructionRequest), httpHeaders);

    when(configUtils.getProcessExpiry()).thenReturn(Boolean.FALSE);

    assertNotNull(serveInstructionResponse);

    verify(deliveryService, times(1))
        .findDeliveryDocument(9967271326l, "10681131135822", httpHeaders);
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(httpHeaders);
    verify(configUtils, times(1)).isDeliveryItemOverrideEnabled(anyInt());
    verify(deliveryItemOverrideService, times(1))
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(instructionHelperService, times(1))
        .publishInstruction(any(), any(), any(), any(), any(), any());

    assertEquals(serveInstructionResponse.getInstruction().getIsReceiveCorrection(), Boolean.FALSE);
    assertEquals(
        serveInstructionResponse.getInstruction().getContainer().getCtrLabel().get("clientId"),
        "Witron");
    List<PrintLabelRequest> printRequestsReturnVal =
        (List<PrintLabelRequest>)
            serveInstructionResponse
                .getInstruction()
                .getContainer()
                .getCtrLabel()
                .get("printRequests");
    assertEquals(printRequestsReturnVal.size(), 0);

    assertEquals(serveInstructionResponse.getInstruction().getMove().get("toLocation"), "Ind-1");
    assertEquals(
        serveInstructionResponse.getInstruction().getContainer().getOutboundChannelMethod(),
        GdcConstants.OUTBOUND_SSTK);

    // Always getProjectedReceiveQty = TixHi
    final DeliveryDocumentLine line0 =
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    final Integer palletHigh = line0.getPalletHigh();
    final Integer palletTie = line0.getPalletTie();
    final int tiXHi = palletHigh * palletTie;
    assertEquals(serveInstructionResponse.getInstruction().getProjectedReceiveQty(), tiXHi);
    assertTrue(line0.getAdditionalInfo().getIsHACCP());
    assertEquals(line0.getAdditionalInfo().getWarehouseAreaDesc(), "Dry Produce");
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void test_serveInstructionRequest_error() throws Exception {

    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("d1fca5ba-5f36-466d-ba2e-4fadbdd11c35");
    instructionRequest.setDeliveryNumber("9967271326");
    instructionRequest.setDeliveryStatus("ARV");
    instructionRequest.setUpcNumber("10681131135822");
    instructionRequest.setDoorNumber("V6949");

    File resource = new ClassPathResource("gdm_scanUpc_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    Optional<DeliveryItemOverride> optionalDeliveryItemOverride = Optional.empty();
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.PUBLISH_CONTAINER);
    doReturn(mockResponse)
        .when(deliveryService)
        .findDeliveryDocument(9967271326l, "10681131135822", httpHeaders);
    doReturn("F67387000020022168").when(lpnCacheService).getLPNBasedOnTenant(httpHeaders);
    doReturn(true).when(configUtils).isDeliveryItemOverrideEnabled(anyInt());
    doReturn(optionalDeliveryItemOverride)
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());

    SlottingPalletBuildResponse slottingPalletBuildResponse =
        getMockSlottingResponse("abc123", "Ind-1");
    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            "32612",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    doThrow(new ReceivingException("Mock exception message"))
        .when(slottingService)
        .acquireSlot(any(InstructionRequest.class), anyString(), anyString(), any());

    ContainerLabel containerLabel = createMockContainerLabel();

    doReturn(containerLabel)
        .when(containerLabelBuilder)
        .generateContainerLabel(anyString(), anyString(), any(DeliveryDocumentLine.class), any());

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());

    doReturn(new Pair<>(830, 1000L))
        .when(instructionHelperService)
        .getReceivedQtyDetails(eq(instructionRequest.getProblemTagId()), any());
    when(appConfig.isCheckDeliveryStatusReceivable()).thenReturn(false);

    InstructionResponse serveInstructionRequest =
        witronInstructionService.serveInstructionRequest(
            gson.toJson(instructionRequest), httpHeaders);

    assertNotNull(serveInstructionRequest);

    verify(deliveryService, times(1))
        .findDeliveryDocument(9967271326l, "10681131135822", httpHeaders);
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(httpHeaders);
    verify(configUtils, times(1)).isDeliveryItemOverrideEnabled(anyInt());
    verify(deliveryItemOverrideService, times(1))
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(jmsPublisher, times(1)).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());

    assertEquals(
        serveInstructionRequest.getInstruction().getContainer().getCtrLabel().get("clientId"),
        "Witron");
    List<PrintLabelRequest> printRequestsReturnVal =
        (List<PrintLabelRequest>)
            serveInstructionRequest
                .getInstruction()
                .getContainer()
                .getCtrLabel()
                .get("printRequests");
    assertEquals(printRequestsReturnVal.size(), 0);

    assertEquals(serveInstructionRequest.getInstruction().getMove().get("toLocation"), "Ind-1");
  }

  @Test
  public void testCreateInstructionWithPTAG() throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    instructionRequest.setProblemTagId("PTAG32612000001");

    doReturn("A32612000000001").when(lpnCacheService).getLPNBasedOnTenant(httpHeaders);

    SlottingPalletBuildResponse slottingPalletBuildResponse =
        getMockSlottingResponse("A32612000000001", "Induct-Slot-1");

    ContainerLabel containerLabel = createMockContainerLabel();

    doReturn(containerLabel)
        .when(containerLabelBuilder)
        .generateContainerLabel(anyString(), anyString(), any(DeliveryDocumentLine.class), any());

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());

    doReturn(new Pair<>(15, 4L))
        .when(instructionHelperService)
        .getReceivedQtyDetails(eq(instructionRequest.getProblemTagId()), any());

    doReturn(slottingPalletBuildResponse)
        .when(slottingService)
        .acquireSlot(any(InstructionRequest.class), anyString(), anyString(), any());

    witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
  }

  @Test
  public void testCreateInstructionWithoutPTAG() throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    instructionRequest.setProblemTagId(null);

    doReturn("A32612000000001").when(lpnCacheService).getLPNBasedOnTenant(httpHeaders);

    SlottingPalletBuildResponse slottingPalletBuildResponse =
        getMockSlottingResponse("A32612000000001", "Induct-Slot-1");

    ContainerLabel containerLabel = createMockContainerLabel();

    doReturn(containerLabel)
        .when(containerLabelBuilder)
        .generateContainerLabel(anyString(), anyString(), any(DeliveryDocumentLine.class), any());

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());

    doReturn(new Pair<>(15, 4L))
        .when(instructionHelperService)
        .getReceivedQtyDetails(eq(instructionRequest.getProblemTagId()), any());

    doReturn(slottingPalletBuildResponse)
        .when(slottingService)
        .acquireSlot(any(InstructionRequest.class), anyString(), anyString(), any());

    witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
  }

  @Test
  public void testCreateInstruction_throwRcvAsCorrectionException() throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    instructionRequest.setReceiveAsCorrection(Boolean.FALSE);
    String errorMessage =
        String.format(
            InstructionErrorCode.getErrorValue("RCV_AS_CORRECTION_ERROR").getErrorMessage(),
            "6983298493");
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", RECEIVE_AS_CORRECTION_FEATURE, false);
    try {
      doThrow(new ReceivingException(errorMessage))
          .when(purchaseReferenceValidator)
          .validateReceiveAsCorrection(any(), any(), anyBoolean(), any(InstructionRequest.class));

      witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      assert (false);
    } catch (ReceivingException e) {
      assert (true);
      verify(purchaseReferenceValidator, times(1))
          .validateReceiveAsCorrection(
              anyString(), anyString(), anyBoolean(), any(InstructionRequest.class));
      assertEquals(e.getMessage(), errorMessage);
    }
  }

  @Test
  public void testCreateInstruction_WithPTAG_Features_groceryProblem_ReceiveAsCorrection()
      throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    instructionRequest.setProblemTagId("PTAG32612000001");

    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", GROCERY_PROBLEM_RECEIVE_FEATURE, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", RECEIVE_AS_CORRECTION_FEATURE, false);

    doReturn("A32612000000001").when(lpnCacheService).getLPNBasedOnTenant(httpHeaders);

    SlottingPalletBuildResponse slottingPalletBuildResponse =
        getMockSlottingResponse("A32612000000001", "Induct-Slot-1");

    ContainerLabel containerLabel = createMockContainerLabel();

    doReturn(containerLabel)
        .when(containerLabelBuilder)
        .generateContainerLabel(anyString(), anyString(), any(DeliveryDocumentLine.class), any());

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());

    doReturn(new Pair<>(15, 4L))
        .when(instructionHelperService)
        .getReceivedQtyDetails(eq(instructionRequest.getProblemTagId()), any());

    doReturn(slottingPalletBuildResponse)
        .when(slottingService)
        .acquireSlot(any(InstructionRequest.class), anyString(), anyString(), any());

    doReturn(500L).when(receiptService).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    Instruction instruction =
        witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);

    ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
    verify(purchaseReferenceValidator, times(1))
        .validateReceiveAsCorrection(
            anyString(), anyString(), argumentCaptor.capture(), any(InstructionRequest.class));
    final Boolean isProblemReceiveFlow = argumentCaptor.getValue();
    assertEquals(isProblemReceiveFlow, Boolean.TRUE, "isProblemReceiveFlow is enabled");

    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    assertEquals(deliveryDocument.getDeliveryDocumentLines().get(0).getOpenQty().intValue(), 0);

    // Grocery problems flow always display the line receivedQty
    assertEquals(instruction.getReceivedQuantity(), 0);
    assertEquals(
        deliveryDocument.getDeliveryDocumentLines().get(0).getTotalReceivedQty().intValue(), 500);
  }

  public ContainerLabel createMockContainerLabel() {
    ContainerLabel containerLabel = new ContainerLabel();
    containerLabel.setClientId("Witron");
    List<PrintLabelRequest> printRequests = new ArrayList<>();
    containerLabel.setPrintRequests(printRequests);
    return containerLabel;
  }

  public SlottingPalletBuildResponse getMockSlottingResponse(
      String trackingId, String divertLocation) {
    SlottingPalletBuildResponse slottingPalletBuildResponse = new SlottingPalletBuildResponse();
    slottingPalletBuildResponse.setContainerTrackingId(trackingId);
    slottingPalletBuildResponse.setDivertLocation(divertLocation);
    return slottingPalletBuildResponse;
  }

  @Test
  public void
      testCreateInstruction_NO_PTAG_with_Features_grocery_problem_flow_ReceiveAsCorrection_exception()
          throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    instructionRequest.setReceiveAsCorrection(Boolean.FALSE);
    String errorMessage =
        String.format(
            InstructionErrorCode.getErrorValue("RCV_AS_CORRECTION_ERROR").getErrorMessage(),
            "6983298493");

    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", GROCERY_PROBLEM_RECEIVE_FEATURE, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", RECEIVE_AS_CORRECTION_FEATURE, false);
    try {
      doThrow(new ReceivingException(errorMessage))
          .when(purchaseReferenceValidator)
          .validateReceiveAsCorrection(any(), any(), anyBoolean(), any(InstructionRequest.class));

      witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      assert (false);
    } catch (ReceivingException e) {
      assert (true);

      ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
      verify(purchaseReferenceValidator, times(1))
          .validateReceiveAsCorrection(
              anyString(), anyString(), argumentCaptor.capture(), any(InstructionRequest.class));
      final Boolean isProblemReceiveFlow = argumentCaptor.getValue();
      assertEquals(isProblemReceiveFlow, Boolean.FALSE, "isProblemReceiveFlow is enabled");
      assertEquals(e.getMessage(), errorMessage);
    }
  }

  @Test
  public void testCreateInstruction_NullLPN() throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    instructionRequest.setReceiveAsCorrection(Boolean.FALSE);
    try {
      doReturn(new Pair<>(15, 4L))
          .when(instructionHelperService)
          .getReceivedQtyDetails(eq(instructionRequest.getProblemTagId()), any());

      doReturn(null).when(lpnCacheService).getLPNBasedOnTenant(httpHeaders);

      witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      assert (false);
    } catch (ReceivingException e) {
      assert (true);
      assertEquals(
          e.getMessage(),
          InstructionErrorCode.getErrorValue(ReceivingException.INVALID_LPN_ERROR)
              .getErrorMessage());
    }
  }

  @Test
  public void testCreateInstruction_duplicateLPN() throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    instructionRequest.setReceiveAsCorrection(Boolean.FALSE);
    try {
      doReturn(new Pair<>(15, 4L))
          .when(instructionHelperService)
          .getReceivedQtyDetails(eq(instructionRequest.getProblemTagId()), any());

      doReturn("B32612000000001").when(lpnCacheService).getLPNBasedOnTenant(httpHeaders);
      doReturn(new Container()).when(containerService).findByTrackingId(anyString());

      witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      assert (false);
    } catch (ReceivingException e) {
      assert (true);
      assertEquals(
          e.getMessage(),
          InstructionErrorCode.getErrorValue(ReceivingException.INVALID_LPN_ERROR)
              .getErrorMessage());
    }
  }

  @Test
  public void testCreateInstruction_sanityCheckForItemData() throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    DeliveryDocumentLine deliveryDocumentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setAdditionalInfo(null);

    try {
      witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      assert (false);
    } catch (ReceivingException e) {
      assert (true);
      assertEquals(e.getErrorResponse().getErrorCode(), "InvalidItem");
      assertEquals(e.getErrorResponse().getErrorHeader(), "Missing item details");
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          "Item cannot be received. Required information is missing in the system. Contact support to help get this resolved.");
    }
  }

  @Test
  public void testCreateInstruction_DeliveryDocument_PoLine_Status_cancelled()
      throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    DeliveryDocumentLine deliveryDocumentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    GdmError gdmPoLineCancelled = GdmErrorCode.getErrorValue(PO_POL_CANCELLED_ERROR);
    doThrow(
            new ReceivingException(
                String.format(
                    gdmPoLineCancelled.getErrorMessage(),
                    deliveryDocumentLine.getPurchaseReferenceNumber(),
                    deliveryDocumentLine.getPurchaseReferenceLineNumber()),
                INTERNAL_SERVER_ERROR,
                gdmPoLineCancelled.getErrorCode(),
                gdmPoLineCancelled.getErrorHeader()))
        .when(deliveryDocumentHelper)
        .validatePoLineStatus(any());
    try {
      witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      assert (false);
    } catch (ReceivingException e) {
      assert (true);
      assertEquals(e.getErrorResponse().getErrorCode(), "createInstruction");
      assertEquals(e.getErrorResponse().getErrorHeader(), "PO/POL Rejected");
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          "The PO: 4763030227 or PO Line: 1 for this item has been cancelled. Please see a supervisor for assistance with this item.");
    }
  }

  @Test
  public void testCreateInstruction_sanityCheckForWeightFormatTypeCode() throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    DeliveryDocumentLine deliveryDocumentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    ItemData itemData = deliveryDocumentLine.getAdditionalInfo();
    itemData.setWeightFormatTypeCode(null);

    try {
      witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      assert (false);
    } catch (ReceivingException e) {
      assert (true);
      assertEquals(e.getErrorResponse().getErrorCode(), "InvalidItem");
      assertEquals(e.getErrorResponse().getErrorHeader(), "Missing item details");
      assertEquals(e.getErrorResponse().getErrorMessage(), "WeightFormatTypeCode is missing.");
    }
  }

  @Test
  public void testCreateInstruction_packSizeMismatchWithFixedWeight() throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    DeliveryDocumentLine deliveryDocumentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setVendorPack(1);
    deliveryDocumentLine.setWarehousePack(1);

    try {
      witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      assert (false);
    } catch (ReceivingException e) {
      assert (true);
      assertEquals(e.getErrorResponse().getErrorCode(), "InvalidPackSize");
      assertEquals(e.getErrorResponse().getErrorHeader(), "Invalid PO and Item Pack");
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          "Item and PO Pack do not match. Please contact support.");
    }
  }

  @Test
  public void testCreateInstruction_packSizeMismatchVariableWeight() throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    DeliveryDocumentLine deliveryDocumentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setVendorPack(1);
    deliveryDocumentLine.setWarehousePack(1);
    ItemData itemData = deliveryDocumentLine.getAdditionalInfo();
    itemData.setWeightFormatTypeCode(ReceivingConstants.VARIABLE_WEIGHT_FORMAT_TYPE_CODE);

    doReturn("A32612000000001").when(lpnCacheService).getLPNBasedOnTenant(httpHeaders);

    SlottingPalletBuildResponse slottingPalletBuildResponse =
        getMockSlottingResponse("A32612000000001", "Induct-Slot-1");

    ContainerLabel containerLabel = createMockContainerLabel();

    doReturn(containerLabel)
        .when(containerLabelBuilder)
        .generateContainerLabel(anyString(), anyString(), any(DeliveryDocumentLine.class), any());

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());

    doReturn(new Pair<>(15, 4L))
        .when(instructionHelperService)
        .getReceivedQtyDetails(eq(instructionRequest.getProblemTagId()), any());

    doReturn(slottingPalletBuildResponse)
        .when(slottingService)
        .acquireSlot(any(InstructionRequest.class), anyString(), anyString(), any());

    Instruction instruction =
        witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);

    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    assertEquals(
        deliveryDocument
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getWeightFormatTypeCode(),
        ReceivingConstants.VARIABLE_WEIGHT_FORMAT_TYPE_CODE);
    assertEquals(deliveryDocument.getDeliveryDocumentLines().get(0).getVendorPack().intValue(), 1);
  }

  @Test
  public void testCreateInstruction_weightFormatMismatch_VtoF() throws ReceivingException {
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            "32612", ReceivingConstants.WEIGHT_FORMAT_TYPE_CHECK_FEATURE, false);

    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    DeliveryDocumentLine deliveryDocumentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    ItemData itemData = deliveryDocumentLine.getAdditionalInfo();
    itemData.setDcWeightFormatTypeCode("V");
    itemData.setOmsWeightFormatTypeCode("F");
    itemData.setWeightFormatTypeCode("F");

    try {
      witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      assert (false);
    } catch (ReceivingException e) {
      assert (true);
      assertEquals(e.getErrorResponse().getErrorCode(), "WeightFormatMismatch");
      assertEquals(e.getErrorResponse().getErrorHeader(), "Weight format mismatch");
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          "This item was previously received as variable weight but is now showing as fixed weight. Please contact your supervisor to get this corrected.");
    }
  }

  @Test
  public void testCreateInstruction_weightFormatMismatch_FtoV_mdm() throws ReceivingException {
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            "32612", ReceivingConstants.WEIGHT_FORMAT_TYPE_CHECK_FEATURE, false);

    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    DeliveryDocumentLine deliveryDocumentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    ItemData itemData = deliveryDocumentLine.getAdditionalInfo();
    itemData.setDcWeightFormatTypeCode("F");
    itemData.setOmsWeightFormatTypeCode(null);
    itemData.setWeightFormatTypeCode("V");

    try {
      witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      assert (false);
    } catch (ReceivingException e) {
      assert (true);
      assertEquals(e.getErrorResponse().getErrorCode(), "WeightFormatMismatch");
      assertEquals(e.getErrorResponse().getErrorHeader(), "Weight format mismatch");
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          "This item was previously received as fixed weight but is now showing as variable weight. Please contact your supervisor to get this corrected.");
    }
  }

  @Test
  public void testCreateInstruction_weightFormatMismatch_FtoV() throws ReceivingException {
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            "32612", ReceivingConstants.WEIGHT_FORMAT_TYPE_CHECK_FEATURE, false);

    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    DeliveryDocumentLine deliveryDocumentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    ItemData itemData = deliveryDocumentLine.getAdditionalInfo();
    itemData.setDcWeightFormatTypeCode("F");
    itemData.setOmsWeightFormatTypeCode("V");
    itemData.setWeightFormatTypeCode("V");

    try {
      witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      assert (false);
    } catch (ReceivingException e) {
      assert (true);
      assertEquals(e.getErrorResponse().getErrorCode(), "WeightFormatMismatch");
      assertEquals(e.getErrorResponse().getErrorHeader(), "Weight format mismatch");
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          "This item was previously received as fixed weight but is now showing as variable weight. Please contact your supervisor to get this corrected.");
    }
  }

  @Test
  public void testCreateInstruction_dcWeightFormatEmpty() throws ReceivingException {
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            "32612", ReceivingConstants.WEIGHT_FORMAT_TYPE_CHECK_FEATURE, false);

    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    DeliveryDocumentLine deliveryDocumentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    ItemData itemData = deliveryDocumentLine.getAdditionalInfo();
    itemData.setDcWeightFormatTypeCode("");
    itemData.setOmsWeightFormatTypeCode("F");
    itemData.setWeightFormatTypeCode("F");

    doReturn("A32612000000002").when(lpnCacheService).getLPNBasedOnTenant(httpHeaders);

    SlottingPalletBuildResponse slottingPalletBuildResponse =
        getMockSlottingResponse("A32612000000002", "Induct-Slot-2");

    ContainerLabel containerLabel = createMockContainerLabel();

    doReturn(containerLabel)
        .when(containerLabelBuilder)
        .generateContainerLabel(anyString(), anyString(), any(DeliveryDocumentLine.class), any());

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());

    doReturn(new Pair<>(15, 4L))
        .when(instructionHelperService)
        .getReceivedQtyDetails(eq(instructionRequest.getProblemTagId()), any());

    doReturn(slottingPalletBuildResponse)
        .when(slottingService)
        .acquireSlot(any(InstructionRequest.class), anyString(), anyString(), any());

    doReturn(true).when(configUtils).getConfiguredFeatureFlag("32612", KOTLIN_ENABLED, false);

    Instruction instructionResponse =
        witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);

    assertEquals(instructionResponse.getProjectedReceiveQty(), 24);
    assertEquals(instructionResponse.getReceivedQuantity(), 0);
    assertEquals(instructionResponse.getInstructionCode(), "AutoGrocBuildPallet");
    assertEquals(instructionResponse.getInstructionMsg(), "Auto Groc Build Pallet");
  }

  @Test
  public void testCreateInstruction_dcWeightFormatNull() throws ReceivingException {
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            "32612", ReceivingConstants.WEIGHT_FORMAT_TYPE_CHECK_FEATURE, false);

    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    DeliveryDocumentLine deliveryDocumentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    ItemData itemData = deliveryDocumentLine.getAdditionalInfo();
    itemData.setDcWeightFormatTypeCode(null);
    itemData.setOmsWeightFormatTypeCode("F");
    itemData.setWeightFormatTypeCode("F");

    doReturn("A32612000000003").when(lpnCacheService).getLPNBasedOnTenant(httpHeaders);

    SlottingPalletBuildResponse slottingPalletBuildResponse =
        getMockSlottingResponse("A32612000000003", "Induct-Slot-3");

    ContainerLabel containerLabel = createMockContainerLabel();

    doReturn(containerLabel)
        .when(containerLabelBuilder)
        .generateContainerLabel(anyString(), anyString(), any(DeliveryDocumentLine.class), any());

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());

    doReturn(new Pair<>(15, 4L))
        .when(instructionHelperService)
        .getReceivedQtyDetails(eq(instructionRequest.getProblemTagId()), any());

    doReturn(slottingPalletBuildResponse)
        .when(slottingService)
        .acquireSlot(any(InstructionRequest.class), anyString(), anyString(), any());

    Instruction instructionResponse =
        witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);

    assertEquals(instructionResponse.getProjectedReceiveQty(), 24);
    assertEquals(instructionResponse.getReceivedQuantity(), 0);
  }

  @Test
  public void testCreateInstruction_nullPromoBuyInd() throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    DeliveryDocumentLine deliveryDocumentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setPromoBuyInd(null);
    try {
      witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      assert (false);
    } catch (ReceivingException e) {
      assert (true);
      assertEquals(e.getErrorResponse().getErrorCode(), "MissingItemDetails");
      assertEquals(e.getErrorResponse().getErrorHeader(), "Missing item details");
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          "Required information is missing for item 550129241. Contact support to help get this resolved before receiving.");
    }
  }

  @Test
  public void testCreateInstruction_emptyPromoBuyInd() throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    DeliveryDocumentLine deliveryDocumentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setPromoBuyInd("");
    try {
      witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      assert (false);
    } catch (ReceivingException e) {
      assert (true);
      assertEquals(e.getErrorResponse().getErrorCode(), "MissingItemDetails");
      assertEquals(e.getErrorResponse().getErrorHeader(), "Missing item details");
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          "Required information is missing for item 550129241. Contact support to help get this resolved before receiving.");
    }
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "Item 550129241 on PO 4763030227 is a HACCP item. QC approval is required to continue.")
  public void testCreateInstruction_HaccpEnabled_throwHaccpException() throws ReceivingException {
    doReturn(true).when(configUtils).getConfiguredFeatureFlag("32612", HACCP_ENABLED, false);
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    DeliveryDocumentLine deliveryDocumentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setSecondaryDescription(
        "NoElementIfNullOrEmpty this Secondary Item Description");
    ItemData itemData = deliveryDocumentLine.getAdditionalInfo();
    itemData.setIsHACCP(Boolean.TRUE);

    try {
      witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      assert (false);
    } catch (ReceivingException e) {
      assert (true);
      final ErrorResponse err = e.getErrorResponse();
      assertNotNull(err);
      assertEquals(err.getErrorCode(), HACCP_ITEM_ALERT.getErrorCode());
      assertEquals(err.getErrorHeader(), HACCP_ITEM_ALERT.getErrorHeader());
      assertEquals(
          err.getErrorMessage(),
          "Item 550129241 on PO 4763030227 is a HACCP item. QC approval is required to continue.");
      final String expectedHaccpErrorJson =
          "{\"deliveryNumber\":\"21119003\",\"purchaseReferenceNumber\":\"4763030227\",\"purchaseReferenceLineNumber\":1,\"itemNbr\":550129241,\"itemDescription\":\"TR 12QT STCKPT SS\",\"secondaryItemDescription\":\"NoElementIfNullOrEmpty this Secondary Item Description\"}";
      assertEquals(err.getErrorInfo(), expectedHaccpErrorJson);
      throw e;
    }
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "Item 550129241 on PO 4763030227 is a HACCP item. QC approval is required to continue.")
  public void testCreateInstruction_KotlinEnabled_throwHaccpException() throws ReceivingException {
    doReturn(true).when(configUtils).getConfiguredFeatureFlag("32612", HACCP_ENABLED, false);
    doReturn(true).when(configUtils).getConfiguredFeatureFlag("32612", KOTLIN_ENABLED, false);
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    DeliveryDocumentLine deliveryDocumentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setSecondaryDescription(
        "NoElementIfNullOrEmpty this Secondary Item Description");
    ItemData itemData = deliveryDocumentLine.getAdditionalInfo();
    itemData.setIsHACCP(Boolean.TRUE);

    try {
      witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      assert (false);
    } catch (ReceivingException e) {
      assert (true);
      final ErrorResponse err = e.getErrorResponse();
      assertNotNull(err);
      assertEquals(err.getErrorCode(), "AutoGrocHACCPItem_4763030227_1");
      assertEquals(err.getErrorHeader(), HACCP_ITEM_ALERT.getErrorHeader());
      assertEquals(
          err.getErrorMessage(),
          "Item 550129241 on PO 4763030227 is a HACCP item. QC approval is required to continue.");
      final String expectedHaccpErrorJson =
          "{\"deliveryNumber\":\"21119003\",\"purchaseReferenceNumber\":\"4763030227\",\"purchaseReferenceLineNumber\":1,\"itemNbr\":550129241,\"itemDescription\":\"TR 12QT STCKPT SS\",\"secondaryItemDescription\":\"NoElementIfNullOrEmpty this Secondary Item Description\"}";
      assertEquals(err.getErrorInfo(), expectedHaccpErrorJson);
      throw e;
    }
  }

  @Test
  public void testCreateInstruction_HaccpDisabled() throws Exception {

    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("d1fca5ba-5f36-466d-ba2e-4fadbdd11c35");
    instructionRequest.setDeliveryNumber("9967271326");
    instructionRequest.setDeliveryStatus("ARV");
    instructionRequest.setUpcNumber("10681131135822");
    instructionRequest.setDoorNumber("V6949");

    File resource = new ClassPathResource("gdm_scanUpc_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    Optional<DeliveryItemOverride> optionalDeliveryItemOverride = Optional.empty();
    doReturn(mockResponse)
        .when(deliveryService)
        .findDeliveryDocument(9967271326l, "10681131135822", httpHeaders);
    doReturn("F67387000020022168").when(lpnCacheService).getLPNBasedOnTenant(httpHeaders);
    doReturn(true).when(configUtils).isDeliveryItemOverrideEnabled(anyInt());
    doReturn(optionalDeliveryItemOverride)
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());

    SlottingPalletBuildResponse slottingPalletBuildResponse =
        getMockSlottingResponse("abc123", "Ind-1");

    doReturn(slottingPalletBuildResponse)
        .when(slottingService)
        .acquireSlot(any(InstructionRequest.class), anyString(), anyString(), any());

    ContainerLabel containerLabel = createMockContainerLabel();

    doReturn(containerLabel)
        .when(containerLabelBuilder)
        .generateContainerLabel(anyString(), anyString(), any(DeliveryDocumentLine.class), any());

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());

    doReturn(Boolean.TRUE)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE);
    doReturn(new DeliveryMetaData())
        .when(witronDeliveryMetaDataService)
        .doManagerOverride(any(), any(), any(), any(), any());
    doReturn(new Pair<>(830, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetails(eq(instructionRequest.getProblemTagId()), any());
    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            "32612",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.PUBLISH_CONTAINER);
    when(appConfig.isCheckDeliveryStatusReceivable()).thenReturn(false);
    when(configUtils.getProcessExpiry()).thenReturn(Boolean.FALSE);

    List<DeliveryDocument> deliveryDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    doReturn(deliveryDocuments).when(deliveryDocumentHelper).updateDeliveryDocuments(any());

    // HaccpDisabled
    doReturn(false).when(configUtils).getConfiguredFeatureFlag("32612", HACCP_ENABLED, false);

    InstructionResponse serveInstructionResponse =
        witronInstructionService.serveInstructionRequest(
            gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    final DeliveryDocumentLine line0 =
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    assertTrue(line0.getAdditionalInfo().getIsHACCP());
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "Item 550129241 on PO 4763030227 is a HACCP item. QC approval is required to continue.")
  public void testCreateInstruction_HaccpEnabled_NoHaccpApproval_throwException()
      throws ReceivingException {
    doReturn(true).when(configUtils).getConfiguredFeatureFlag("32612", HACCP_ENABLED, false);
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    DeliveryDocumentLine deliveryDocumentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    ItemData itemData = deliveryDocumentLine.getAdditionalInfo();
    itemData.setIsHACCP(Boolean.TRUE);
    ;

    try {
      witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      assert (false);
    } catch (ReceivingException e) {
      assert (true);
      assertEquals(e.getErrorResponse().getErrorCode(), HACCP_ITEM_ALERT.getErrorCode());
      assertEquals(e.getErrorResponse().getErrorHeader(), HACCP_ITEM_ALERT.getErrorHeader());
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          "Item 550129241 on PO 4763030227 is a HACCP item. QC approval is required to continue.");
      throw e;
    }
  }

  @Test
  public void testCreateInstruction_HaccpEnabled_HasHaccpApproval_NO_error_MANAGER_OVERRIDE_V2()
      throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("d1fca5ba-5f36-466d-ba2e-4fadbdd11c35");
    instructionRequest.setDeliveryNumber("9967271326");
    instructionRequest.setDeliveryStatus("ARV");
    instructionRequest.setUpcNumber("10681131135822");
    instructionRequest.setDoorNumber("V6949");

    File resource = new ClassPathResource("gdm_scanUpc_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    Optional<DeliveryItemOverride> optionalDeliveryItemOverride = Optional.empty();
    doReturn(mockResponse)
        .when(deliveryService)
        .findDeliveryDocument(9967271326l, "10681131135822", httpHeaders);
    doReturn("F67387000020022168").when(lpnCacheService).getLPNBasedOnTenant(httpHeaders);
    doReturn(true).when(configUtils).isDeliveryItemOverrideEnabled(anyInt());
    doReturn(optionalDeliveryItemOverride)
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());

    SlottingPalletBuildResponse slottingPalletBuildResponse =
        getMockSlottingResponse("abc123", "Ind-1");

    doReturn(slottingPalletBuildResponse)
        .when(slottingService)
        .acquireSlot(any(InstructionRequest.class), anyString(), anyString(), any());

    ContainerLabel containerLabel = createMockContainerLabel();

    doReturn(containerLabel)
        .when(containerLabelBuilder)
        .generateContainerLabel(anyString(), anyString(), any(DeliveryDocumentLine.class), any());

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());

    doReturn(Boolean.TRUE)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE);
    doReturn(new DeliveryMetaData())
        .when(witronDeliveryMetaDataService)
        .doManagerOverride(any(), any(), any(), any(), any());
    doReturn(new Pair<>(830, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetails(eq(instructionRequest.getProblemTagId()), any());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.PUBLISH_CONTAINER);
    when(appConfig.isCheckDeliveryStatusReceivable()).thenReturn(false);
    when(configUtils.getProcessExpiry()).thenReturn(Boolean.FALSE);
    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            "32612",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    // HACCP_ENABLED
    doReturn(true).when(configUtils).getConfiguredFeatureFlag("32612", HACCP_ENABLED, false);
    doReturn(true).when(configUtils).getConfiguredFeatureFlag("32612", MANAGER_OVERRIDE_V2, false);
    doReturn(true)
        .when(witronDeliveryMetaDataService)
        .isManagerOverrideV2(anyString(), anyString(), anyInt(), anyString());

    List<DeliveryDocument> deliveryDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    doReturn(deliveryDocuments).when(deliveryDocumentHelper).updateDeliveryDocuments(any());

    InstructionResponse serveInstructionResponse =
        witronInstructionService.serveInstructionRequest(
            gson.toJson(instructionRequest), httpHeaders);

    assertNotNull(serveInstructionResponse);
    final DeliveryDocumentLine line0 =
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    final boolean haccp = line0.getAdditionalInfo().getIsHACCP();

    assertTrue(haccp);
  }

  @Test
  public void
      testCreateInstruction_HaccpEnabled_HasHaccpApproval_NO_error_MANAGER_OVERRIDE_V2_disabled()
          throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("d1fca5ba-5f36-466d-ba2e-4fadbdd11c35");
    instructionRequest.setDeliveryNumber("9967271326");
    instructionRequest.setDeliveryStatus("ARV");
    instructionRequest.setUpcNumber("10681131135822");
    instructionRequest.setDoorNumber("V6949");

    File resource = new ClassPathResource("gdm_scanUpc_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    Optional<DeliveryItemOverride> optionalDeliveryItemOverride = Optional.empty();
    doReturn(mockResponse)
        .when(deliveryService)
        .findDeliveryDocument(9967271326l, "10681131135822", httpHeaders);
    doReturn("F67387000020022168").when(lpnCacheService).getLPNBasedOnTenant(httpHeaders);
    doReturn(true).when(configUtils).isDeliveryItemOverrideEnabled(anyInt());
    doReturn(optionalDeliveryItemOverride)
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());

    SlottingPalletBuildResponse slottingPalletBuildResponse =
        getMockSlottingResponse("abc123", "Ind-1");

    doReturn(slottingPalletBuildResponse)
        .when(slottingService)
        .acquireSlot(any(InstructionRequest.class), anyString(), anyString(), any());

    ContainerLabel containerLabel = createMockContainerLabel();

    doReturn(containerLabel)
        .when(containerLabelBuilder)
        .generateContainerLabel(anyString(), anyString(), any(DeliveryDocumentLine.class), any());

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());

    doReturn(Boolean.TRUE)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE);
    doReturn(new DeliveryMetaData())
        .when(witronDeliveryMetaDataService)
        .doManagerOverride(any(), any(), any(), any(), any());
    doReturn(new Pair<>(830, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetails(eq(instructionRequest.getProblemTagId()), any());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.PUBLISH_CONTAINER);
    when(appConfig.isCheckDeliveryStatusReceivable()).thenReturn(false);
    when(configUtils.getProcessExpiry()).thenReturn(Boolean.FALSE);
    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            "32612",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    // HACCP_ENABLED
    doReturn(true).when(configUtils).getConfiguredFeatureFlag("32612", HACCP_ENABLED, false);
    doReturn(false).when(configUtils).getConfiguredFeatureFlag("32612", MANAGER_OVERRIDE_V2, false);
    doReturn(true)
        .when(witronDeliveryMetaDataService)
        .isManagerOverride(anyString(), anyString(), anyInt(), anyString());

    List<DeliveryDocument> deliveryDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    doReturn(deliveryDocuments).when(deliveryDocumentHelper).updateDeliveryDocuments(any());

    InstructionResponse serveInstructionResponse =
        witronInstructionService.serveInstructionRequest(
            gson.toJson(instructionRequest), httpHeaders);

    assertNotNull(serveInstructionResponse);
    final DeliveryDocumentLine line0 =
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    final boolean haccp = line0.getAdditionalInfo().getIsHACCP();

    assertTrue(haccp);
  }

  @Test
  public void testCreateInstruction_HaccpEnabled_NoHaccpApproval_ProblemReceiving_NoError()
      throws Exception {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    instructionRequest.setProblemTagId("1111111");
    DeliveryDocumentLine deliveryDocumentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    ItemData itemData = deliveryDocumentLine.getAdditionalInfo();
    itemData.setIsHACCP(Boolean.TRUE);

    File resource = new ClassPathResource("gdm_scanUpc_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    Optional<DeliveryItemOverride> optionalDeliveryItemOverride = Optional.empty();
    doReturn(mockResponse)
        .when(deliveryService)
        .findDeliveryDocument(9967271326l, "10681131135822", httpHeaders);
    doReturn("F67387000020022168").when(lpnCacheService).getLPNBasedOnTenant(httpHeaders);
    doReturn(true).when(configUtils).isDeliveryItemOverrideEnabled(anyInt());
    doReturn(optionalDeliveryItemOverride)
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());

    SlottingPalletBuildResponse slottingPalletBuildResponse =
        getMockSlottingResponse("abc123", "Ind-1");

    doReturn(slottingPalletBuildResponse)
        .when(slottingService)
        .acquireSlot(any(InstructionRequest.class), anyString(), anyString(), any());

    ContainerLabel containerLabel = createMockContainerLabel();

    doReturn(containerLabel)
        .when(containerLabelBuilder)
        .generateContainerLabel(anyString(), anyString(), any(DeliveryDocumentLine.class), any());

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());

    doReturn(Boolean.TRUE)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE);
    doReturn(new DeliveryMetaData())
        .when(witronDeliveryMetaDataService)
        .doManagerOverride(any(), any(), any(), any(), any());
    doReturn(new Pair<>(830, 1000L))
        .when(instructionHelperService)
        .getReceivedQtyDetails(eq(instructionRequest.getProblemTagId()), any());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.PUBLISH_CONTAINER);
    when(appConfig.isCheckDeliveryStatusReceivable()).thenReturn(false);
    when(configUtils.getProcessExpiry()).thenReturn(Boolean.FALSE);

    // HACCP_ENABLED
    doReturn(true).when(configUtils).getConfiguredFeatureFlag("32612", HACCP_ENABLED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", GROCERY_PROBLEM_RECEIVE_FEATURE, false);
    doReturn(true).when(configUtils).getConfiguredFeatureFlag("32612", MANAGER_OVERRIDE_V2, false);
    doReturn(false)
        .when(witronDeliveryMetaDataService)
        .isManagerOverrideV2(anyString(), anyString(), anyInt(), anyString());

    // execute
    InstructionResponse serveInstructionResponse =
        witronInstructionService.serveInstructionRequest(
            gson.toJson(instructionRequest), httpHeaders);
    // verify
    assertNotNull(serveInstructionResponse);
    final DeliveryDocumentLine line0 =
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    final boolean haccp = line0.getAdditionalInfo().getIsHACCP();

    assertTrue(haccp);
  }

  @Test
  public void testCreateInstructionForUpcReceiving_fetchOpenInstruction_Kotlin_Off()
      throws ReceivingException {
    when(instructionPersisterService.fetchExistingInstructionIfexists(
            any(InstructionRequest.class)))
        .thenReturn(MockInstruction.getInstruction());

    Instruction instruction =
        witronInstructionService.createInstructionForUpcReceiving(
            MockInstruction.getInstructionRequest(), httpHeaders);

    assertNotNull(instruction);
    verify(instructionPersisterService, times(1))
        .fetchExistingInstructionIfexists(any(InstructionRequest.class));
    assertEquals(instruction.getId().intValue(), 1901);
  }

  @Test
  public void testCreateInstructionForUpcReceiving_fetchOpenInstruction_Kotlin_On()
      throws ReceivingException {
    doReturn(true).when(configUtils).getConfiguredFeatureFlag("32612", KOTLIN_ENABLED, false);
    when(instructionPersisterService.fetchExistingOpenInstruction(
            any(DeliveryDocument.class), any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockInstruction.getInstruction());

    Instruction instruction =
        witronInstructionService.createInstructionForUpcReceiving(
            MockInstruction.getInstructionRequest(), httpHeaders);

    assertNotNull(instruction);
    verify(instructionPersisterService, times(1))
        .fetchExistingOpenInstruction(
            any(DeliveryDocument.class), any(InstructionRequest.class), any(HttpHeaders.class));
    assertEquals(instruction.getId().intValue(), 1901);
  }

  @Test
  public void testCreateInstruction_NewItem_throwException() throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    DeliveryDocumentLine deliveryDocumentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    ItemData itemData = deliveryDocumentLine.getAdditionalInfo();
    itemData.setProfiledWarehouseArea(null);
    deliveryDocumentLine.setProfiledWarehouseArea(null);

    try {
      witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(e.getErrorResponse().getErrorCode(), "createInstruction");
      assertEquals(e.getErrorResponse().getErrorHeader(), "Register Item");
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          "Item number 550129241 is new. Please register item at the nearest Cubiscan station.");
    }
  }

  @Test
  public void testCreateInstruction_MultiUserException_KotlinDisabled() throws ReceivingException {
    try {
      InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
      doReturn(new Pair<>(15, 4L))
          .when(instructionHelperService)
          .getReceivedQtyDetails(eq(instructionRequest.getProblemTagId()), any());
      when(instructionRepository.getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine(
              anyString(), anyInt()))
          .thenReturn(Long.valueOf(15));
      witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(e.getErrorResponse().getErrorCode(), "createInstruction");
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          "A new pallet cannot be created until the pallets owned by other users for this item are completed. Please work on another item or request for pallet transfer.");
    }
  }

  @Test
  public void testCreateInstruction_MultiUserException_KotlinEnabled() throws ReceivingException {
    try {
      InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
      doReturn(true).when(configUtils).getConfiguredFeatureFlag("32612", KOTLIN_ENABLED, false);
      doReturn(new Pair<>(15, 4L))
          .when(instructionHelperService)
          .getReceivedQtyDetails(eq(instructionRequest.getProblemTagId()), any());
      when(instructionRepository.getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine(
              anyString(), anyInt()))
          .thenReturn(Long.valueOf(15));
      witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(e.getErrorResponse().getErrorCode(), "GLS-RCV-MULTI-INST-400");
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          "A new pallet cannot be created until the pallets owned by other users for this item are completed. Please work on another item or request for pallet transfer.");
    }
  }

  @Test
  public void testCreateInstruction_OverageException_KotlinEnabled() throws ReceivingException {
    try {
      InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
      doReturn(true).when(configUtils).getConfiguredFeatureFlag("32612", KOTLIN_ENABLED, false);
      doReturn(new Pair<>(15, 20L))
          .when(instructionHelperService)
          .getReceivedQtyDetails(eq(instructionRequest.getProblemTagId()), any());

      witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(e.getErrorResponse().getErrorCode(), "AutoGrocOverage_4763030227_1");
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          "The allowed number of cases for this item have been received. Please report the remaining as overage.");
    }
  }

  @Test
  public void testCreateInstruction_OverageException_KotlinDisabled() throws ReceivingException {
    try {
      InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
      doReturn(false).when(configUtils).getConfiguredFeatureFlag("32612", KOTLIN_ENABLED, false);
      doReturn(new Pair<>(15, 20L))
          .when(instructionHelperService)
          .getReceivedQtyDetails(eq(instructionRequest.getProblemTagId()), any());

      witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(e.getErrorResponse().getErrorCode(), "overage");
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          "The allowed number of cases for this item have been received. Please report the remaining as overage.");
    }
  }

  @Test
  public void testCancelInstruction_WhenQuantityIsMoreThanZero() throws ReceivingException {
    final Long instructionId = 1L;
    try {
      Instruction instruction = MockInstruction.getInstruction();
      instruction.setReceivedQuantity(10);
      when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);
      Instruction cancelledInstruction = MockInstruction.getCancelledInstruction();
      when(instructionRepository.save(any(Instruction.class))).thenReturn(cancelledInstruction);

      witronInstructionService.cancelInstruction(instructionId, httpHeaders);
    } catch (ReceivingException e) {
      fail();
    }
    verify(instructionPersisterService, times(1)).getInstructionById(instructionId);
    verify(instructionRepository, times(1)).save(any());
    verify(receiptService, times(1)).saveReceipt(any());
  }

  @Test
  public void testCancelInstruction_WhenQuantityIsZero() throws ReceivingException {
    final Long instructionId = 1L;
    try {
      Instruction instruction = MockInstruction.getInstruction();
      instruction.setReceivedQuantity(0);
      when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);
      Instruction cancelledInstruction = MockInstruction.getCancelledInstruction();
      when(instructionRepository.save(any(Instruction.class))).thenReturn(cancelledInstruction);

      witronInstructionService.cancelInstruction(instructionId, httpHeaders);
    } catch (ReceivingException e) {
      fail();
    }
    verify(instructionPersisterService, times(1)).getInstructionById(instructionId);
    verify(instructionRepository, times(1)).save(any());
  }

  @Test
  public void testCancelInstruction_WhenInstructionAlreadyCompleted() throws ReceivingException {
    final String userId = "k0c0e5c";
    try {
      Instruction instruction = MockInstruction.getInstruction();
      instruction.setCompleteTs(new Date());
      instruction.setReceivedQuantity(1);
      instruction.setCompleteUserId(userId);
      when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);

      witronInstructionService.cancelInstruction(1L, httpHeaders);
      fail();
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), INTERNAL_SERVER_ERROR);
      final ErrorResponse errorResponse = e.getErrorResponse();
      assertEquals(errorResponse.getErrorCode(), "Instruction is complete");
      assertEquals(errorResponse.getErrorHeader(), "Pallet was completed");
      assertEquals(
          errorResponse.getErrorMessage(),
          "This pallet was completed by "
              + userId
              + ", please start a new pallet to continue receiving.");
    }
  }

  @Test
  public void testCancelInstruction_WhenInstructionAlreadyCancelled() throws ReceivingException {
    final String userId = "k0c0e5c";
    try {
      Instruction instruction = MockInstruction.getInstruction();
      instruction.setCompleteTs(new Date());
      instruction.setCompleteUserId(userId);
      when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);

      witronInstructionService.cancelInstruction(1L, httpHeaders);
      fail();
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), INTERNAL_SERVER_ERROR);
      final ErrorResponse errorResponse = e.getErrorResponse();
      assertEquals(errorResponse.getErrorCode(), "Instruction is complete");
      assertEquals(errorResponse.getErrorHeader(), "Pallet was cancelled");
      assertEquals(
          errorResponse.getErrorMessage(),
          "This pallet was cancelled by "
              + userId
              + ", please start a new pallet to continue receiving.");
    }
  }

  @Test
  public void testCancelInstruction_WhenInstructionAlreadyCompleted_GDC_cancel_error()
      throws ReceivingException {
    final String userId = "k0c0e5c";
    try {
      Instruction instruction = MockInstruction.getInstruction();
      instruction.setCompleteTs(new Date());
      instruction.setReceivedQuantity(1);
      instruction.setCompleteUserId(userId);
      when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);
      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag("32612", IS_GDC_CANCEL_INSTRUCTION_ERROR_ENABLED, false);

      witronInstructionService.cancelInstruction(1L, httpHeaders);
      fail();
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
      final ErrorResponse errorResponse = e.getErrorResponse();
      assertEquals(errorResponse.getErrorCode(), "GLS-RCV-INSTRUCTION-COMPLETED-CODE-400");
      assertEquals(errorResponse.getErrorHeader(), "Pallet was completed");
      assertEquals(
          errorResponse.getErrorMessage(),
          "This pallet was completed by "
              + userId
              + ", please start a new pallet to continue receiving.");
    }
  }

  @Test
  public void testCancelInstruction_WhenInstructionAlreadyCancelled_GDC_cancel_error()
      throws ReceivingException {
    final String userId = "k0c0e5c";
    try {
      Instruction instruction = MockInstruction.getInstruction();
      instruction.setCompleteTs(new Date());
      instruction.setCompleteUserId(userId);
      when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);
      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag("32612", IS_GDC_CANCEL_INSTRUCTION_ERROR_ENABLED, false);

      witronInstructionService.cancelInstruction(1L, httpHeaders);
      fail();
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
      final ErrorResponse errorResponse = e.getErrorResponse();
      assertEquals(errorResponse.getErrorCode(), "GLS-RCV-INSTRUCTION-COMPLETED-CODE-400");
      assertEquals(errorResponse.getErrorHeader(), "Pallet was cancelled");
      assertEquals(
          errorResponse.getErrorMessage(),
          "This pallet was cancelled by "
              + userId
              + ", please start a new pallet to continue receiving.");
    }
  }

  @Test
  public void testCreateInstruction_partial_PTAG_receive_ProjectedReceiveQty_MinResolutionQtyWins()
      throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    instructionRequest.setProblemTagId("PTAG32612000001");
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", GROCERY_PROBLEM_RECEIVE_FEATURE, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", RECEIVE_AS_CORRECTION_FEATURE, false);
    doReturn("A32612000000001").when(lpnCacheService).getLPNBasedOnTenant(httpHeaders);
    SlottingPalletBuildResponse slottingPalletBuildResponse =
        getMockSlottingResponse("A32612000000001", "Induct-Slot-1");
    ContainerLabel containerLabel = createMockContainerLabel();
    doReturn(containerLabel)
        .when(containerLabelBuilder)
        .generateContainerLabel(anyString(), anyString(), any(DeliveryDocumentLine.class), any());
    doReturn(slottingPalletBuildResponse)
        .when(slottingService)
        .acquireSlot(any(InstructionRequest.class), anyString(), anyString(), any());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
    doReturn(new Pair<>(15, 30L))
        .when(instructionHelperService)
        .getReceivedQtyDetails(eq(instructionRequest.getProblemTagId()), any());
    doReturn(5L).when(receiptService).getReceivedQtyByPoAndPoLine(anyString(), anyInt());

    Instruction instruction =
        witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);

    assertEquals(instruction.getProjectedReceiveQty(), 10);
  }

  @Test
  public void testCreateInstruction_partial_PTAG_receive_ProjectedReceiveQty_TiHiWins()
      throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    instructionRequest.setResolutionQty(25); // Greater than 24=6x4 TixHi
    instructionRequest.setProblemTagId("PTAG32612000001");
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", GROCERY_PROBLEM_RECEIVE_FEATURE, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", RECEIVE_AS_CORRECTION_FEATURE, false);
    doReturn("A32612000000001").when(lpnCacheService).getLPNBasedOnTenant(httpHeaders);
    SlottingPalletBuildResponse slottingPalletBuildResponse =
        getMockSlottingResponse("A32612000000001", "Induct-Slot-1");
    ContainerLabel containerLabel = createMockContainerLabel();
    doReturn(containerLabel)
        .when(containerLabelBuilder)
        .generateContainerLabel(anyString(), anyString(), any(DeliveryDocumentLine.class), any());
    doReturn(slottingPalletBuildResponse)
        .when(slottingService)
        .acquireSlot(any(InstructionRequest.class), anyString(), anyString(), any());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
    doReturn(new Pair<>(15, 30L))
        .when(instructionHelperService)
        .getReceivedQtyDetails(eq(instructionRequest.getProblemTagId()), any());
    doReturn(5L).when(receiptService).getReceivedQtyByPoAndPoLine(anyString(), anyInt());

    Instruction instruction =
        witronInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);

    assertEquals(instruction.getProjectedReceiveQty(), 24);
  }

  @Test
  public void test_serveInstructionRequest_WithSscc() throws Exception {

    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("d1fca5ba-5f36-466d-ba2e-4fadbdd11c35");
    instructionRequest.setDeliveryNumber("9967271326");
    instructionRequest.setDeliveryStatus("ARV");
    instructionRequest.setUpcNumber("10681131135822");
    instructionRequest.setDoorNumber("V6949");
    instructionRequest.setReceivingType(ReceivingType.SSCC.getReceivingType());
    instructionRequest.setSscc("00998876543212345678");

    File resource = new ClassPathResource("gdm_ssccScan_mappedResponse.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    Optional<DeliveryItemOverride> optionalDeliveryItemOverride = Optional.empty();
    doReturn(deliveryService)
        .when(configUtils)
        .getConfiguredInstance(
            "32612", ReceivingConstants.DELIVERY_SERVICE_KEY, DeliveryService.class);
    doReturn(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)))
        .when(deliveryService)
        .findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class));
    doReturn(true).when(asnReceivingAuditLogger).isVendorEnabledForAsnReceiving(any(), any());
    doReturn("F67387000020022168").when(lpnCacheService).getLPNBasedOnTenant(httpHeaders);
    doReturn(true).when(configUtils).isDeliveryItemOverrideEnabled(anyInt());
    doReturn(optionalDeliveryItemOverride)
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());

    SlottingPalletBuildResponse slottingPalletBuildResponse =
        getMockSlottingResponse("abc123", "Ind-1");

    doReturn(slottingPalletBuildResponse)
        .when(slottingService)
        .acquireSlot(any(InstructionRequest.class), anyString(), anyString(), any());

    ContainerLabel containerLabel = createMockContainerLabel();
    doReturn(vendorBasedDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            "32612",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    doReturn(containerLabel)
        .when(containerLabelBuilder)
        .generateContainerLabel(anyString(), anyString(), any(DeliveryDocumentLine.class), any());
    doReturn(witronVendorValidator)
        .when(configUtils)
        .getConfiguredInstance("32612", ReceivingConstants.VENDOR_VALIDATOR, VendorValidator.class);
    doReturn(true).when(witronVendorValidator).isAsnReceivingEnabled();

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());

    doReturn(Boolean.TRUE)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE);
    doReturn(new DeliveryMetaData())
        .when(witronDeliveryMetaDataService)
        .doManagerOverride(any(), any(), any(), any(), any());
    doReturn(new Pair<>(830, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetails(eq(instructionRequest.getProblemTagId()), any());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.PUBLISH_CONTAINER);
    when(appConfig.isCheckDeliveryStatusReceivable()).thenReturn(false);

    List<DeliveryDocument> deliveryDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    doReturn(deliveryDocuments).when(deliveryDocumentHelper).updateDeliveryDocuments(any());

    InstructionResponse serveInstructionResponse =
        witronInstructionService.serveInstructionRequest(
            gson.toJson(instructionRequest), httpHeaders);

    when(configUtils.getProcessExpiry()).thenReturn(Boolean.FALSE);

    assertNotNull(serveInstructionResponse);

    verify(deliveryService, times(1))
        .findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class));
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(httpHeaders);
    verify(configUtils, times(1)).isDeliveryItemOverrideEnabled(anyInt());
    verify(deliveryItemOverrideService, times(1))
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(instructionHelperService, times(1))
        .publishInstruction(any(), any(), any(), any(), any(), any());

    assertEquals(serveInstructionResponse.getInstruction().getIsReceiveCorrection(), Boolean.FALSE);
    assertEquals(
        serveInstructionResponse.getInstruction().getContainer().getCtrLabel().get("clientId"),
        "Witron");
    List<PrintLabelRequest> printRequestsReturnVal =
        (List<PrintLabelRequest>)
            serveInstructionResponse
                .getInstruction()
                .getContainer()
                .getCtrLabel()
                .get("printRequests");
    assertEquals(printRequestsReturnVal.size(), 0);

    assertEquals(serveInstructionResponse.getInstruction().getMove().get("toLocation"), "Ind-1");
    assertEquals(
        serveInstructionResponse.getInstruction().getContainer().getOutboundChannelMethod(),
        GdcConstants.OUTBOUND_SSTK);

    final DeliveryDocumentLine line0 =
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    assertEquals(serveInstructionResponse.getInstruction().getProjectedReceiveQty(), 49);
    assertTrue(line0.getAdditionalInfo().getIsHACCP());
    assertEquals(line0.getAdditionalInfo().getWarehouseAreaDesc(), "Frozen");
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Receiving freight by SSCC is not supported.")
  public void test_serveInstructionRequest_WithSscc_NotSupportedVendor() throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("d1fca5ba-5f36-466d-ba2e-4fadbdd11c35");
    instructionRequest.setDeliveryNumber("9967271326");
    instructionRequest.setDeliveryStatus("ARV");
    instructionRequest.setUpcNumber("10681131135822");
    instructionRequest.setDoorNumber("V6949");
    instructionRequest.setReceivingType(ReceivingType.SSCC.getReceivingType());
    instructionRequest.setSscc("00998876543212345678");

    File resource = new ClassPathResource("gdm_ssccScan_mappedResponse.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    Optional<DeliveryItemOverride> optionalDeliveryItemOverride = Optional.empty();
    doReturn(deliveryService)
        .when(configUtils)
        .getConfiguredInstance(
            "32612", ReceivingConstants.DELIVERY_SERVICE_KEY, DeliveryService.class);
    doReturn(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)))
        .when(deliveryService)
        .findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class));
    doReturn(true).when(asnReceivingAuditLogger).isVendorEnabledForAsnReceiving(any(), any());
    doReturn("F67387000020022168").when(lpnCacheService).getLPNBasedOnTenant(httpHeaders);
    doReturn(true).when(configUtils).isDeliveryItemOverrideEnabled(anyInt());
    doReturn(optionalDeliveryItemOverride)
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());

    SlottingPalletBuildResponse slottingPalletBuildResponse =
        getMockSlottingResponse("abc123", "Ind-1");

    doReturn(slottingPalletBuildResponse)
        .when(slottingService)
        .acquireSlot(any(InstructionRequest.class), anyString(), anyString(), any());

    ContainerLabel containerLabel = createMockContainerLabel();
    doReturn(vendorBasedDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            "32612",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    doReturn(containerLabel)
        .when(containerLabelBuilder)
        .generateContainerLabel(anyString(), anyString(), any(DeliveryDocumentLine.class), any());
    doReturn(witronVendorValidator)
        .when(configUtils)
        .getConfiguredInstance("32612", ReceivingConstants.VENDOR_VALIDATOR, VendorValidator.class);
    doReturn(false).when(witronVendorValidator).isAsnReceivingEnabled();

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());

    doReturn(Boolean.TRUE)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE);
    doReturn(new DeliveryMetaData())
        .when(witronDeliveryMetaDataService)
        .doManagerOverride(any(), any(), any(), any(), any());
    doReturn(new Pair<>(830, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetails(eq(instructionRequest.getProblemTagId()), any());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.PUBLISH_CONTAINER);
    when(appConfig.isCheckDeliveryStatusReceivable()).thenReturn(false);
    InstructionResponse serveInstructionResponse =
        witronInstructionService.serveInstructionRequest(
            gson.toJson(instructionRequest), httpHeaders);
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "The allowed number of cases for this item have been received. Please report the remaining as overage by scanning UPC.")
  public void test_serveInstructionRequest_WithSscc_Overages() throws Exception {
    HttpHeaders httpHeaders = GdcHttpHeaders.getHeadersWithKotlinFlag();
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("d1fca5ba-5f36-466d-ba2e-4fadbdd11c35");
    instructionRequest.setDeliveryNumber("9967271326");
    instructionRequest.setDeliveryStatus("ARV");
    instructionRequest.setUpcNumber("10681131135822");
    instructionRequest.setDoorNumber("V6949");
    instructionRequest.setReceivingType(ReceivingType.SSCC.getReceivingType());
    instructionRequest.setSscc("00998876543212345678");

    File resource = new ClassPathResource("gdm_ssccScan_mappedResponse.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    Optional<DeliveryItemOverride> optionalDeliveryItemOverride = Optional.empty();
    doReturn(deliveryService)
        .when(configUtils)
        .getConfiguredInstance(
            "32612", ReceivingConstants.DELIVERY_SERVICE_KEY, DeliveryService.class);
    doReturn(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)))
        .when(deliveryService)
        .findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class));
    doReturn(true).when(asnReceivingAuditLogger).isVendorEnabledForAsnReceiving(any(), any());
    doReturn("F67387000020022168").when(lpnCacheService).getLPNBasedOnTenant(httpHeaders);
    doReturn(true).when(configUtils).isDeliveryItemOverrideEnabled(anyInt());
    doReturn(optionalDeliveryItemOverride)
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());

    SlottingPalletBuildResponse slottingPalletBuildResponse =
        getMockSlottingResponse("abc123", "Ind-1");

    doReturn(slottingPalletBuildResponse)
        .when(slottingService)
        .acquireSlot(any(InstructionRequest.class), anyString(), anyString(), any());

    ContainerLabel containerLabel = createMockContainerLabel();
    doReturn(vendorBasedDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            "32612",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    doReturn(containerLabel)
        .when(containerLabelBuilder)
        .generateContainerLabel(anyString(), anyString(), any(DeliveryDocumentLine.class), any());
    doReturn(witronVendorValidator)
        .when(configUtils)
        .getConfiguredInstance("32612", ReceivingConstants.VENDOR_VALIDATOR, VendorValidator.class);
    doReturn(true).when(witronVendorValidator).isAsnReceivingEnabled();

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());

    doReturn(Boolean.TRUE)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE);
    doReturn(new DeliveryMetaData())
        .when(witronDeliveryMetaDataService)
        .doManagerOverride(any(), any(), any(), any(), any());
    doReturn(new Pair<>(830, 830L))
        .when(instructionHelperService)
        .getReceivedQtyDetails(eq(instructionRequest.getProblemTagId()), any());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.PUBLISH_CONTAINER);
    when(appConfig.isCheckDeliveryStatusReceivable()).thenReturn(false);

    List<DeliveryDocument> deliveryDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    doReturn(deliveryDocuments).when(deliveryDocumentHelper).updateDeliveryDocuments(any());

    InstructionResponse serveInstructionResponse =
        witronInstructionService.serveInstructionRequest(
            gson.toJson(instructionRequest), httpHeaders);
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "You cannot receive label 00998876543212345678 as it is already received. Please scan UPC to receive the remaining freight")
  public void test_serveInstructionRequest_WithSscc_alreadyReceived() throws Exception {
    HttpHeaders httpHeaders = GdcHttpHeaders.getHeadersWithKotlinFlag();
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("d1fca5ba-5f36-466d-ba2e-4fadbdd11c35");
    instructionRequest.setDeliveryNumber("9967271326");
    instructionRequest.setDeliveryStatus("ARV");
    instructionRequest.setUpcNumber("10681131135822");
    instructionRequest.setDoorNumber("V6949");
    instructionRequest.setReceivingType(ReceivingType.SSCC.getReceivingType());
    instructionRequest.setSscc("00998876543212345678");

    File resource = new ClassPathResource("gdm_ssccScan_mappedResponse.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    Optional<DeliveryItemOverride> optionalDeliveryItemOverride = Optional.empty();
    doReturn(25).when(containerService).receivedContainerQuantityBySSCC(anyString());
    doReturn(deliveryService)
        .when(configUtils)
        .getConfiguredInstance(
            "32612", ReceivingConstants.DELIVERY_SERVICE_KEY, DeliveryService.class);
    doReturn(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)))
        .when(deliveryService)
        .findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class));
    doReturn(true).when(asnReceivingAuditLogger).isVendorEnabledForAsnReceiving(any(), any());
    doReturn("F67387000020022168").when(lpnCacheService).getLPNBasedOnTenant(httpHeaders);
    doReturn(true).when(configUtils).isDeliveryItemOverrideEnabled(anyInt());
    doReturn(optionalDeliveryItemOverride)
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());

    SlottingPalletBuildResponse slottingPalletBuildResponse =
        getMockSlottingResponse("abc123", "Ind-1");

    doReturn(slottingPalletBuildResponse)
        .when(slottingService)
        .acquireSlot(any(InstructionRequest.class), anyString(), anyString(), any());

    ContainerLabel containerLabel = createMockContainerLabel();
    doReturn(vendorBasedDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            "32612",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    doReturn(containerLabel)
        .when(containerLabelBuilder)
        .generateContainerLabel(anyString(), anyString(), any(DeliveryDocumentLine.class), any());
    doReturn(witronVendorValidator)
        .when(configUtils)
        .getConfiguredInstance("32612", ReceivingConstants.VENDOR_VALIDATOR, VendorValidator.class);
    doReturn(true).when(witronVendorValidator).isAsnReceivingEnabled();

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());

    doReturn(Boolean.TRUE)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE);
    doReturn(new DeliveryMetaData())
        .when(witronDeliveryMetaDataService)
        .doManagerOverride(any(), any(), any(), any(), any());
    doReturn(new Pair<>(830, 830L))
        .when(instructionHelperService)
        .getReceivedQtyDetails(eq(instructionRequest.getProblemTagId()), any());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.PUBLISH_CONTAINER);
    when(appConfig.isCheckDeliveryStatusReceivable()).thenReturn(false);

    List<DeliveryDocument> deliveryDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    doReturn(deliveryDocuments).when(deliveryDocumentHelper).updateDeliveryDocuments(any());

    InstructionResponse serveInstructionResponse =
        witronInstructionService.serveInstructionRequest(
            gson.toJson(instructionRequest), httpHeaders);
  }
}
