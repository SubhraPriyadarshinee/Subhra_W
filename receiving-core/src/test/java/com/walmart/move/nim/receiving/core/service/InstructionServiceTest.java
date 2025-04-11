package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_KOTLIN_CLIENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClientException;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.GdmError;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.validators.DeliveryValidator;
import com.walmart.move.nim.receiving.core.common.validators.InstructionStateValidator;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.DockTag;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.factory.DocumentSelectorProvider;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.item.rules.LimitedQtyRule;
import com.walmart.move.nim.receiving.core.item.rules.LithiumIonLimitedQtyRule;
import com.walmart.move.nim.receiving.core.item.rules.LithiumIonRule;
import com.walmart.move.nim.receiving.core.message.publisher.MessagePublisher;
import com.walmart.move.nim.receiving.core.mock.data.MockContainer;
import com.walmart.move.nim.receiving.core.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.core.mock.data.MockInstructionRequest;
import com.walmart.move.nim.receiving.core.mock.data.MockInstructionResponse;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingDivertLocations;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryReasonCodeState;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.DockTagType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.libs.commons.lang3.tuple.ImmutablePair;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import org.mockito.*;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class InstructionServiceTest {

  @Mock private InstructionRepository instructionRepository;
  @Mock private InstructionPersisterService instructionPersisterService;
  @InjectMocks private InstructionHelperService instructionHelperService;
  @Mock private DeliveryStatusPublisher deliveryStatusPublisher;

  @Mock private FdeService fdeService;

  @Mock private ProblemService problemService;

  @Spy private InstructionStateValidator instructionStateValidator;

  @Mock private DefaultDeliveryDocumentSelector defaultDeliveryDocumentSelector;

  @Mock private FbqBasedDeliveryDocumentSelector fbqBasedDeliveryDocumentSelector;

  @Mock protected ReceiptsAggregator receiptsAggregator;
  @Mock private ImportSlottingServiceImpl importSlottingService;
  private HttpHeaders headers;
  private Container container;

  private InstructionRequest instructionRequest;

  private final HttpHeaders httpHeaders = GdcHttpHeaders.getHeadersWithKotlinFlag();

  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private MovePublisher movePublisher;

  @InjectMocks private InstructionService instructionService;

  @Mock private ReceiptService receiptService;

  @Mock Pair<Integer, Long> receivedQtyDetails;
  @Mock private MessagePublisher messagePublisher;

  @InjectMocks @Spy DefaultDeliveryDocumentsSearchHandler defaultDeliveryDocumentsSearchHandler;
  private static final String facilityNum = "32818";

  private final Gson gson = new Gson();

  @Mock private ContainerService containerService;
  @Mock private DeliveryService deliveryService;
  @Mock private AppConfig appConfig;
  @InjectMocks @Spy private DeliveryDocumentHelper deliveryDocumentHelper;

  @Mock private InstructionUtils instructionUtils;

  @Mock private ImportsInstructionUtils importsInstructionUtils;
  @Mock private RegulatedItemService regulatedItemService;
  @Mock private DockTagPersisterService dockTagPersisterService;
  @Mock private GDMRestApiClient gdmRestApiClient;
  @Mock private LabelServiceImpl labelServiceImpl;

  @Spy private LithiumIonLimitedQtyRule lithiumIonLimitedQtyRule;
  @Spy private LimitedQtyRule limitedQtyRule;
  @Spy private LithiumIonRule lithiumIonRule;

  @Mock private ManualInstructionService manualInstructionService;
  @InjectMocks @Spy private DefaultReceiveInstructionHandler defaultReceiveInstructionHandler;
  @InjectMocks @Spy private DefaultOpenQtyCalculator defaultOpenQtyCalculator;
  @Mock private DocumentSelectorProvider documentSelectorProvider;

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        instructionService,
        InstructionService.class,
        "tenantSpecificConfigReader",
        configUtils,
        TenantSpecificConfigReader.class);
    TenantContext.setFacilityNum(32612);
    ReflectionTestUtils.setField(instructionService, "regulatedItemService", regulatedItemService);
    ReflectionTestUtils.setField(instructionService, "gson", gson);
    ReflectionTestUtils.setField(
        instructionService, InstructionService.class, "gson", gson, Gson.class);
    // ReflectionTestUtils.setField(rdcInstructionService, "multiSkuHandler", multiSkuHandler);
    ReflectionTestUtils.setField(
        instructionService,
        InstructionService.class,
        "deliveryValidator",
        new DeliveryValidator(),
        DeliveryValidator.class);
    ReflectionTestUtils.setField(defaultDeliveryDocumentsSearchHandler, "gson", gson);
    ReflectionTestUtils.setField(
        instructionService, "deliveryDocumentHelper", deliveryDocumentHelper);
    ReflectionTestUtils.setField(
        instructionService, "instructionHelperService", instructionHelperService);

    container = new Container();
    container.setLocation("100");
  }

  @BeforeMethod
  public void init() {
    when(configUtils.getConfiguredInstance(
            anyString(),
            eq(OPEN_QTY_CALCULATOR),
            eq(DEFAULT_OPEN_QTY_CALCULATOR),
            eq(OpenQtyCalculator.class)))
        .thenReturn(defaultOpenQtyCalculator);
    when(documentSelectorProvider.getDocumentSelector(any()))
        .thenReturn(defaultDeliveryDocumentSelector);
    when(configUtils.getConfiguredInstance(
            anyString(),
            eq(DELIVERY_DOCUMENT_SELECTOR),
            eq(DEFAULT_DELIVERY_DOCUMENT_SELECTOR),
            eq(DeliveryDocumentSelector.class)))
        .thenReturn(defaultDeliveryDocumentSelector);
  }

  @AfterMethod
  public void cleanup() {
    reset(instructionRepository);
    reset(instructionPersisterService);
    reset(instructionStateValidator);
    reset(containerService);
    reset(configUtils);
    reset(instructionUtils);
    reset(deliveryDocumentHelper);
    reset(manualInstructionService);
    reset(defaultDeliveryDocumentsSearchHandler);
    reset(dockTagPersisterService);
    reset(gdmRestApiClient);
    reset(labelServiceImpl);
  }

  @Test
  public void test_getInstructionSummaryByDeliveryAndInstructionSetId() {

    ArgumentCaptor<Long> deliveryNumberCaptor = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<Long> instructionSetIdCaptor = ArgumentCaptor.forClass(Long.class);

    List<Instruction> mockSummaryList = new ArrayList<>();
    mockSummaryList.add(new Instruction());

    doReturn(mockSummaryList)
        .when(instructionRepository)
        .findByDeliveryNumberAndInstructionSetIdOrderByCreateTs(
            deliveryNumberCaptor.capture(), instructionSetIdCaptor.capture());

    List<InstructionSummary> instructionSummaryByDeliveryAndInstructionSetId =
        instructionService.getInstructionSummaryByDeliveryAndInstructionSetId(12345l, 98765l);
    assertNotNull(instructionSummaryByDeliveryAndInstructionSetId);

    assertTrue(deliveryNumberCaptor.getValue() == 12345l);
    assertTrue(instructionSetIdCaptor.getValue() == 98765l);

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndInstructionSetIdOrderByCreateTs(anyLong(), anyLong());
  }

  @Test
  public void test_getInstructionSummaryByDeliveryAndInstructionSetId_optional_InstructionSetId() {

    ArgumentCaptor<Long> deliveryNumberCaptor = ArgumentCaptor.forClass(Long.class);

    List<Instruction> mockSummaryList = new ArrayList<>();
    mockSummaryList.add(new Instruction());

    doReturn(mockSummaryList)
        .when(instructionRepository)
        .findByDeliveryNumber(deliveryNumberCaptor.capture());

    List<InstructionSummary> instructionSummaryByDeliveryAndInstructionSetId =
        instructionService.getInstructionSummaryByDeliveryAndInstructionSetId(12345l, null);
    assertNotNull(instructionSummaryByDeliveryAndInstructionSetId);

    assertTrue(deliveryNumberCaptor.getValue() == 12345l);

    verify(instructionRepository, times(1)).findByDeliveryNumber(anyLong());
  }

  @Test
  public void test_getInstructionById() {
    try {
      doReturn(MockInstruction.getInstruction())
          .when(instructionPersisterService)
          .getInstructionById(anyLong());
      Instruction instruction = instructionService.getInstructionById(12345L);
      assertNotNull(instruction);
      assertNotNull(instruction.getId());
      verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    } catch (ReceivingException e) {
      Assert.assertTrue(false, "Exception not expected");
    }
  }

  @Test
  public void testRefreshInstruction_InstructionIdNotFound() {
    try {
      when(instructionRepository.findById(anyLong()))
          .thenThrow(
              new ReceivingBadDataException(
                  ExceptionCodes.INSTRUCTION_NOT_FOUND,
                  String.format(ReceivingConstants.INSTRUCTION_NOT_FOUND, 12345L)));

      instructionService.refreshInstruction(12345L, MockHttpHeaders.getHeaders("32612", "US"));
      fail();
    } catch (ReceivingBadDataException rbde) {
      assertEquals(rbde.getErrorCode(), "GLS-RCV-INSTRN-NOT-FOUND-404");
      assertEquals(rbde.getMessage(), "Instruction not found for id: 12345");
    } catch (ReceivingException e) {
      fail();
    }
  }

  @Test
  public void testCancelInstruction_WhenInstructionAlreadyCompleted() throws ReceivingException {
    final String userId = "sysadmin";
    try {
      Instruction instruction = MockInstruction.getInstruction();
      instruction.setCompleteTs(new Date());
      instruction.setReceivedQuantity(1);
      instruction.setCompleteUserId(userId);
      when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);

      instructionService.cancelInstruction(1L, httpHeaders);
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
    final String userId = "sysadmin";
    try {
      Instruction instruction = MockInstruction.getInstruction();
      instruction.setCompleteTs(new Date());
      instruction.setCompleteUserId(userId);
      instruction.setReceivedQuantity(0);
      when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);

      instructionService.cancelInstruction(1L, httpHeaders);
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
  public void testCreateInstructionForUpcReceiving_fetchExistingOpenInstruction_Kotlin_Off()
      throws ReceivingException {
    doReturn(false).when(configUtils).getConfiguredFeatureFlag("32612", KOTLIN_ENABLED, false);
    when(instructionPersisterService.fetchExistingInstructionIfexists(
            any(InstructionRequest.class)))
        .thenReturn(MockInstruction.getInstruction());

    Instruction instruction =
        instructionService.createInstructionForUpcReceiving(
            MockInstruction.getInstructionRequest(), null);

    assertNotNull(instruction);
    verify(instructionPersisterService, times(1))
        .fetchExistingInstructionIfexists(any(InstructionRequest.class));
    assertEquals(instruction.getId().intValue(), 1901);
  }

  @Test
  public void testCreateInstructionForUpcReceiving_setTotalReceivedQty_Kotlin_On()
      throws ReceivingException, IOException {
    httpHeaders.add(USER_ID_HEADER_KEY, "sysadmin");
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    doReturn(false).when(configUtils).useFbqInCbr(any(Integer.class));
    doReturn(false).when(configUtils).isDeliveryItemOverrideEnabled(any(Integer.class));
    when(receiptService.getReceivedQtyByPoAndPoLine(any(), any())).thenReturn(9l);
    when(instructionPersisterService.fetchExistingOpenInstruction(
            any(DeliveryDocument.class), any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(
            getJSONFromPath(
                "../receiving-test/src/main/resources/json/manual_instruction_response.json"));
    Instruction mockedInstruction = MockInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        gson.fromJson(mockedInstruction.getDeliveryDocument(), DeliveryDocument.class);
    deliveryDocument.getDeliveryDocumentLines().get(0).setTotalReceivedQty(9);
    mockedInstruction.setDeliveryDocument(gson.toJson(deliveryDocument));
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(mockedInstruction);
    when(configUtils.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.INSTRUCTION_PUBLISHER), eq(MessagePublisher.class)))
        .thenReturn(messagePublisher);
    doNothing().when(messagePublisher).publish(any(), any());
    Instruction instruction =
        instructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
    assertNotNull(instruction);
    assertEquals(
        9,
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class)
            .getDeliveryDocumentLines()
            .get(0)
            .getTotalReceivedQty()
            .intValue());
  }

  private String getJSONFromPath(String path) throws IOException {
    String fileFromPath = new File(path).getCanonicalPath();
    return new String(Files.readAllBytes(Paths.get(fileFromPath)));
  }

  @Test
  public void testCreateInstructionForUpcReceiving_fetchExistingOpenInstruction_Kotlin_On()
      throws ReceivingException {
    doReturn(true).when(configUtils).getConfiguredFeatureFlag("32612", KOTLIN_ENABLED, false);
    when(instructionPersisterService.fetchExistingOpenInstruction(
            any(DeliveryDocument.class), any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockInstruction.getInstruction());
    Instruction instruction =
        instructionService.createInstructionForUpcReceiving(
            MockInstruction.getInstructionRequest(), httpHeaders);
    assertNotNull(instruction);
    verify(instructionPersisterService, times(1))
        .fetchExistingOpenInstruction(
            any(DeliveryDocument.class), any(InstructionRequest.class), any(HttpHeaders.class));
    assertEquals(instruction.getId().intValue(), 1901);
  }

  @Test
  public void testCreateInstructionForPoConReceiving_fetchExistingOpenInstruction_Kotlin_Off()
      throws ReceivingException {
    doReturn(false).when(configUtils).getConfiguredFeatureFlag("32612", KOTLIN_ENABLED, false);
    when(instructionRepository.findByMessageId(anyString()))
        .thenReturn(MockInstruction.getInstruction());

    Instruction instruction =
        instructionService.createInstructionForPoConReceiving(
            MockInstruction.getInstructionRequest(), null);

    assertNotNull(instruction);
    verify(instructionRepository, times(1)).findByMessageId(anyString());
    assertEquals(instruction.getMessageId(), "11e1df00-ebf6-11e8-9c25-dd4bfc2b96a1");
  }

  @Test
  public void testCreateInstructionForPoConReceiving_fetchExistingOpenInstruction_Kotlin_On()
      throws ReceivingException {
    doReturn(true).when(configUtils).getConfiguredFeatureFlag("32612", KOTLIN_ENABLED, false);
    when(instructionPersisterService.fetchExistingOpenInstruction(
            any(DeliveryDocument.class), any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockInstruction.getInstruction());
    Instruction instruction =
        instructionService.createInstructionForPoConReceiving(
            MockInstruction.getInstructionRequest(), httpHeaders);
    assertNotNull(instruction);
    verify(instructionPersisterService, times(1))
        .fetchExistingOpenInstruction(
            any(DeliveryDocument.class), any(InstructionRequest.class), any(HttpHeaders.class));
    assertEquals(instruction.getId().intValue(), 1901);
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testCreateInstructionForUpcReceiving_setLastChangedUserId_Kotlin_On()
      throws ReceivingException {
    httpHeaders.add(USER_ID_HEADER_KEY, "sysadmin");
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    instructionRequest.setProblemTagId("32698130845503");
    doReturn(false).when(configUtils).useFbqInCbr(any(Integer.class));
    doReturn(false).when(configUtils).isDeliveryItemOverrideEnabled(any(Integer.class));
    when(instructionPersisterService.fetchExistingOpenInstruction(
            any(DeliveryDocument.class), any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenThrow(ReceivingException.class);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(MockInstruction.getInstruction());
    doReturn(problemService)
        .when(configUtils)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.PROBLEM_SERVICE), eq(ProblemService.class));
    when(problemService.getProblemDetails(anyString()))
        .thenReturn(
            gson.fromJson(
                MockInstruction.getMockProblemLabel().getProblemResponse(),
                FitProblemTagResponse.class));
    instructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
  }

  // Regulated Item Validation
  @Test
  public void
      testServeInstructionRequest_SinglePoReturnsNoInstructionDueToLimitedQtyComplianceError_KotlinOn()
          throws ReceivingException, IOException {
    String facilityNumVendorComplReq = "32899";
    String countryCodeVendorCompReq = "US";
    headers = MockHttpHeaders.getHeaders(facilityNumVendorComplReq, countryCodeVendorCompReq);
    headers.add(IS_KOTLIN_CLIENT, "true");
    TenantContext.setFacilityCountryCode(countryCodeVendorCompReq);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNumVendorComplReq));

    List<DeliveryDocument> deliveryDocumentList =
        MockInstruction
            .getInstructionRequestWithLimitedQtyComplianceWithDeliveryDocumentForSinglePO()
            .getDeliveryDocuments();
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes(MockInstruction.getMockTransportationModeForLimitedQty());

    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNumVendorComplReq,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(instructionUtils.isVendorComplianceRequired(any(DeliveryDocumentLine.class)))
        .thenReturn(Boolean.TRUE);

    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNumVendorComplReq,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);

    doReturn(deliveryDocumentList)
        .when(defaultDeliveryDocumentsSearchHandler)
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));

    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), ReceivingConstants.REGULATED_ITEM_VALIDATION, false);

    InstructionResponse instructionResponse =
        instructionService.serveInstructionRequest(
            gson.toJson(
                MockInstruction
                    .getInstructionRequestWithLimitedQtyComplianceWithoutDeliveryDocument()),
            headers);
    assertTrue(ReceivingUtils.isSinglePO(deliveryDocumentList));
    assertNotNull(instructionResponse);
    assertNull(instructionResponse.getInstruction());
    assertEquals(1, instructionResponse.getDeliveryDocuments().size());
  }

  @Test
  public void
      testServeInstructionRequest_SinglePoReturnsNoInstructionDueToLithiumIonComplianceError_KotlinOn()
          throws ReceivingException, IOException {
    String facilityNumVendorComplReq = "32899";
    String countryCodeVendorCompReq = "US";
    headers = MockHttpHeaders.getHeaders(facilityNumVendorComplReq, countryCodeVendorCompReq);
    headers.add(IS_KOTLIN_CLIENT, "true");
    TenantContext.setFacilityCountryCode(countryCodeVendorCompReq);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNumVendorComplReq));

    List<DeliveryDocument> deliveryDocumentList =
        MockInstruction.getInstructionRequestWithLithiumIonComplianceWithDocumentForSinglePO()
            .getDeliveryDocuments();
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes(MockInstruction.getMockTransportationModeForLithiumIon());
    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNumVendorComplReq,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(instructionUtils.isVendorComplianceRequired(any(DeliveryDocumentLine.class)))
        .thenReturn(Boolean.TRUE);

    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNumVendorComplReq,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);

    doReturn(deliveryDocumentList)
        .when(defaultDeliveryDocumentsSearchHandler)
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));

    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), ReceivingConstants.REGULATED_ITEM_VALIDATION, false);

    InstructionResponse instructionResponse =
        instructionService.serveInstructionRequest(
            gson.toJson(
                MockInstruction.getInstructionRequestWithLithiumIonComplianceWithoutDocument()),
            headers);

    assertTrue(ReceivingUtils.isSinglePO(deliveryDocumentList));
    assertNotNull(instructionResponse);
    assertNull(instructionResponse.getInstruction());
    assertEquals(1, instructionResponse.getDeliveryDocuments().size());
  }

  @Test
  public void
      testServeInstructionRequest_MultiplePoLineReturnsNoInstructionDueToLimitedQtyComplianceError_KotlinOn()
          throws ReceivingException, IOException {
    String facilityNumVendorComplReq = "32899";
    String countryCodeVendorCompReq = "US";
    headers = MockHttpHeaders.getHeaders(facilityNumVendorComplReq, countryCodeVendorCompReq);
    headers.add(IS_KOTLIN_CLIENT, "true");
    TenantContext.setFacilityCountryCode(countryCodeVendorCompReq);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNumVendorComplReq));

    instructionRequest =
        MockInstruction
            .getInstructionRequestWithLimitedQtyComplianceWithDeliveryDocumentForMultiPO();

    List<DeliveryDocument> deliveryDocuments_gdm = instructionRequest.getDeliveryDocuments();
    deliveryDocuments_gdm
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes(MockInstruction.getMockTransportationModeForLimitedQty());

    deliveryDocuments_gdm
        .get(1)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes(MockInstruction.getMockTransportationModeForLimitedQty());

    DeliveryDocumentLine deliveryDocumentLine1 =
        deliveryDocuments_gdm.get(0).getDeliveryDocumentLines().get(0);
    List<DeliveryDocumentLine> deliveryDocumentLines1 = new ArrayList<>();
    deliveryDocumentLines1.add(deliveryDocumentLine1);
    deliveryDocumentLines1.add(deliveryDocumentLine1);
    deliveryDocuments_gdm.get(0).setDeliveryDocumentLines(deliveryDocumentLines1);
    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);

    DeliveryDocumentLine deliveryDocumentLine2 =
        deliveryDocuments_gdm.get(1).getDeliveryDocumentLines().get(0);
    List<DeliveryDocumentLine> deliveryDocumentLines2 = new ArrayList<>();
    deliveryDocumentLines2.add(deliveryDocumentLine2);
    deliveryDocumentLines2.add(deliveryDocumentLine2);
    deliveryDocuments_gdm.get(1).setDeliveryDocumentLines(deliveryDocumentLines2);

    assertEquals(2, deliveryDocuments_gdm.size());

    when(instructionUtils.isVendorComplianceRequired(any(DeliveryDocumentLine.class)))
        .thenReturn(Boolean.TRUE);
    Pair<DeliveryDocument, DeliveryDocumentLine> selectedLine =
        new Pair<>(deliveryDocument, deliveryDocumentLine1);
    doReturn(selectedLine)
        .when(defaultDeliveryDocumentSelector)
        .autoSelectDeliveryDocumentLine(anyList());

    doReturn(deliveryDocuments_gdm)
        .when(defaultDeliveryDocumentsSearchHandler)
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));

    when(instructionPersisterService.fetchExistingOpenInstruction(
            any(DeliveryDocument.class), any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockInstruction.getInstruction());

    when(instructionPersisterService.fetchExistingInstructionIfexists(
            any(InstructionRequest.class)))
        .thenReturn(MockInstruction.getInstruction());

    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), ReceivingConstants.REGULATED_ITEM_VALIDATION, false);

    InstructionResponse instructionResponse =
        instructionService.autoSelectLineAndCreateInstruction(
            deliveryDocuments_gdm,
            MockInstruction.getInstructionRequestWithLimitedQtyComplianceWithoutDeliveryDocument(),
            headers);

    assertNotNull(instructionResponse);
    assertNull(instructionResponse.getInstruction());
    assertEquals(1, instructionResponse.getDeliveryDocuments().size());

    when(instructionUtils.isVendorComplianceRequired(any(DeliveryDocumentLine.class)))
        .thenReturn(Boolean.TRUE);
    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNumVendorComplReq,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    doReturn(deliveryDocuments_gdm)
        .when(defaultDeliveryDocumentsSearchHandler)
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));

    when(instructionPersisterService.fetchExistingOpenInstruction(
            any(DeliveryDocument.class), any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockInstruction.getInstruction());

    when(instructionPersisterService.fetchExistingInstructionIfexists(
            any(InstructionRequest.class)))
        .thenReturn(MockInstruction.getInstruction());

    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), ReceivingConstants.REGULATED_ITEM_VALIDATION, false);

    InstructionResponse instructionResponseFromSI =
        instructionService.serveInstructionRequest(
            gson.toJson(
                MockInstruction
                    .getInstructionRequestWithLimitedQtyComplianceWithoutDeliveryDocument()),
            headers);

    assertNotNull(instructionResponseFromSI);
    assertNull(instructionResponseFromSI.getInstruction());
    assertEquals(1, instructionResponseFromSI.getDeliveryDocuments().size());
  }

  @Test
  public void
      testAutoSelectLineAndCreateInstruction_MultiplePoLineReturnsNoInstructionDueToLithiumIonComplianceError_KotlinOn()
          throws ReceivingException, IOException {
    String facilityNumVendorComplReq = "32899";
    String countryCodeVendorCompReq = "US";
    headers = MockHttpHeaders.getHeaders(facilityNumVendorComplReq, countryCodeVendorCompReq);
    headers.add(IS_KOTLIN_CLIENT, "true");
    TenantContext.setFacilityCountryCode(countryCodeVendorCompReq);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNumVendorComplReq));

    instructionRequest =
        MockInstruction.getInstructionRequestWithLithiumIonComplianceWithDocumentForMultiPO();

    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes(MockInstruction.getMockTransportationModeForLithiumIon());
    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocuments.get(0).setDeliveryDocumentLines(deliveryDocumentLines);

    assertEquals(2, deliveryDocuments.size());

    when(instructionUtils.isVendorComplianceRequired(any(DeliveryDocumentLine.class)))
        .thenReturn(Boolean.TRUE);
    Pair<DeliveryDocument, DeliveryDocumentLine> selectedLine =
        new Pair<>(deliveryDocument, deliveryDocumentLine);
    doReturn(selectedLine)
        .when(defaultDeliveryDocumentSelector)
        .autoSelectDeliveryDocumentLine(anyList());

    when(instructionPersisterService.fetchExistingOpenInstruction(
            any(DeliveryDocument.class), any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockInstruction.getInstruction());

    when(instructionPersisterService.fetchExistingInstructionIfexists(
            any(InstructionRequest.class)))
        .thenReturn(MockInstruction.getInstruction());

    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), ReceivingConstants.REGULATED_ITEM_VALIDATION, false);

    InstructionResponse instructionResponse =
        instructionService.autoSelectLineAndCreateInstruction(
            deliveryDocuments,
            MockInstruction.getInstructionRequestWithLithiumIonComplianceWithDocumentForMultiPO(),
            headers);

    assertNotNull(instructionResponse);
    assertNull(instructionResponse.getInstruction());
    assertEquals(1, instructionResponse.getDeliveryDocuments().size());

    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNumVendorComplReq,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);

    doReturn(deliveryDocuments)
        .when(defaultDeliveryDocumentsSearchHandler)
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));

    when(instructionPersisterService.fetchExistingOpenInstruction(
            any(DeliveryDocument.class), any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockInstruction.getInstruction());

    when(instructionPersisterService.fetchExistingInstructionIfexists(
            any(InstructionRequest.class)))
        .thenReturn(MockInstruction.getInstruction());

    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), ReceivingConstants.REGULATED_ITEM_VALIDATION, false);

    InstructionResponse instructionResponseFromSI =
        instructionService.serveInstructionRequest(
            gson.toJson(
                MockInstruction.getInstructionRequestWithLithiumIonComplianceWithoutDocument()),
            headers);

    assertNotNull(instructionResponseFromSI);
    assertNull(instructionResponseFromSI.getInstruction());
    assertEquals(1, instructionResponseFromSI.getDeliveryDocuments().size());
  }

  @Test
  public void
      testServeInstruction_MultiplePoLineReturnsNoInstructionDueToLimitedQtyComplianceError_KotlinOff()
          throws ReceivingException, IOException {
    String facilityNumVendorComplReq = "32899";
    String countryCodeVendorCompReq = "US";
    headers = MockHttpHeaders.getHeaders(facilityNumVendorComplReq, countryCodeVendorCompReq);
    headers.add(IS_KOTLIN_CLIENT, "false");
    TenantContext.setFacilityCountryCode(countryCodeVendorCompReq);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNumVendorComplReq));

    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNumVendorComplReq,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);

    doReturn(MockInstruction.getDeliveryDocumentsForMultiPO())
        .when(defaultDeliveryDocumentsSearchHandler)
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));

    when(instructionPersisterService.fetchExistingOpenInstruction(
            any(DeliveryDocument.class), any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockInstruction.getInstruction());

    when(instructionPersisterService.fetchExistingInstructionIfexists(
            any(InstructionRequest.class)))
        .thenReturn(MockInstruction.getInstruction());

    InstructionResponse instructionResponseFromVendorCompl =
        instructionService.serveInstructionRequest(
            gson.toJson(
                MockInstruction
                    .getInstructionRequestWithLimitedQtyComplianceWithoutDeliveryDocument()),
            headers);

    assertNotNull(instructionResponseFromVendorCompl);
    assertNull(instructionResponseFromVendorCompl.getInstruction());
    assertEquals(2, instructionResponseFromVendorCompl.getDeliveryDocuments().size());
  }

  @Test
  public void
      testServeInstruction_MultiplePoLineReturnsNoInstructionDueToLithiumIonComplianceError_KotlinOff()
          throws ReceivingException, IOException {
    String facilityNumVendorComplReq = "32899";
    String countryCodeVendorCompReq = "US";
    headers = MockHttpHeaders.getHeaders(facilityNumVendorComplReq, countryCodeVendorCompReq);
    headers.add(IS_KOTLIN_CLIENT, "false");
    TenantContext.setFacilityCountryCode(countryCodeVendorCompReq);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNumVendorComplReq));

    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNumVendorComplReq,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);

    doReturn(MockInstruction.getDeliveryDocumentsForMultiPO())
        .when(defaultDeliveryDocumentsSearchHandler)
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));

    when(instructionPersisterService.fetchExistingOpenInstruction(
            any(DeliveryDocument.class), any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockInstruction.getInstruction());

    when(instructionPersisterService.fetchExistingInstructionIfexists(
            any(InstructionRequest.class)))
        .thenReturn(MockInstruction.getInstruction());

    InstructionResponse instructionResponseFromVendorCompl =
        instructionService.serveInstructionRequest(
            gson.toJson(
                MockInstruction.getInstructionRequestWithLithiumIonComplianceWithoutDocument()),
            headers);

    assertNotNull(instructionResponseFromVendorCompl);
    assertNull(instructionResponseFromVendorCompl.getInstruction());
    assertEquals(2, instructionResponseFromVendorCompl.getDeliveryDocuments().size());
  }

  @Test
  public void
      testServeInstruction_SinglePoLineReturnsNoInstructionDueToLimitedQtyComplianceError_KotlinOff()
          throws ReceivingException, IOException {
    String facilityNumVendorComplReq = "32899";
    String countryCodeVendorCompReq = "US";
    headers = MockHttpHeaders.getHeaders(facilityNumVendorComplReq, countryCodeVendorCompReq);
    headers.add(IS_KOTLIN_CLIENT, "false");
    TenantContext.setFacilityCountryCode(countryCodeVendorCompReq);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNumVendorComplReq));

    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNumVendorComplReq,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);

    doReturn(MockInstruction.getDeliveryDocumentsForSinglePO())
        .when(defaultDeliveryDocumentsSearchHandler)
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));

    when(instructionPersisterService.fetchExistingOpenInstruction(
            any(DeliveryDocument.class), any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockInstruction.getInstruction());

    when(instructionPersisterService.fetchExistingInstructionIfexists(
            any(InstructionRequest.class)))
        .thenReturn(MockInstruction.getInstruction());

    InstructionResponse instructionResponseFromVendorCompl =
        instructionService.serveInstructionRequest(
            gson.toJson(
                MockInstruction
                    .getInstructionRequestWithLimitedQtyComplianceWithoutDeliveryDocument()),
            headers);

    assertNotNull(instructionResponseFromVendorCompl);
    assertNotNull(instructionResponseFromVendorCompl.getInstruction());
    assertEquals(1, instructionResponseFromVendorCompl.getDeliveryDocuments().size());
  }

  @Test
  public void
      testServeInstruction_SinglePoLineReturnsNoInstructionDueToLithiumIonComplianceError_KotlinOff()
          throws ReceivingException, IOException {
    String facilityNumVendorComplReq = "32899";
    String countryCodeVendorCompReq = "US";
    headers = MockHttpHeaders.getHeaders(facilityNumVendorComplReq, countryCodeVendorCompReq);
    headers.add(IS_KOTLIN_CLIENT, "false");
    TenantContext.setFacilityCountryCode(countryCodeVendorCompReq);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNumVendorComplReq));

    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNumVendorComplReq,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);

    doReturn(MockInstruction.getDeliveryDocumentsForSinglePO())
        .when(defaultDeliveryDocumentsSearchHandler)
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));

    when(instructionPersisterService.fetchExistingOpenInstruction(
            any(DeliveryDocument.class), any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockInstruction.getInstruction());

    when(instructionPersisterService.fetchExistingInstructionIfexists(
            any(InstructionRequest.class)))
        .thenReturn(MockInstruction.getInstruction());

    InstructionResponse instructionResponseFromVendorCompl =
        instructionService.serveInstructionRequest(
            gson.toJson(
                MockInstruction.getInstructionRequestWithLithiumIonComplianceWithoutDocument()),
            headers);

    assertNotNull(instructionResponseFromVendorCompl);
    assertNotNull(instructionResponseFromVendorCompl.getInstruction());
    assertEquals(1, instructionResponseFromVendorCompl.getDeliveryDocuments().size());
  }

  @Test
  public void testServeInstructionRequest_SinglePOLine_AutoCaseReceiveFeature_ManualReceiving()
      throws Exception {
    headers = MockHttpHeaders.getHeaders("32899", "US");
    headers.add(IS_KOTLIN_CLIENT, "true");
    TenantContext.setFacilityNum(32899);

    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER),
            eq(DeliveryDocumentsSearchHandler.class));

    InstructionRequest manualInstructionRequest =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocuments();
    List<DeliveryDocument> mockDeliveryDocuments = manualInstructionRequest.getDeliveryDocuments();
    manualInstructionRequest.setManualReceivingEnabled(false);
    manualInstructionRequest.setFeatureType(AUTO_CASE_RECEIVE_FEATURE_TYPE);
    manualInstructionRequest.setDeliveryDocuments(null);

    doReturn(mockDeliveryDocuments)
        .when(defaultDeliveryDocumentsSearchHandler)
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));
    try {
      instructionService.serveInstructionRequest(gson.toJson(manualInstructionRequest), headers);
    } catch (ReceivingException receivingException) {
      assertEquals(receivingException.getErrorResponse().getErrorHeader(), "Item Not Conveyable");
    }
  }

  @Test
  public void
      testServeInstructionRequest_MultiPOLine_OneDAConLine_AutoCaseReceiveFeature_ManualReceiving()
          throws Exception {
    headers = MockHttpHeaders.getHeaders("32899", "US");
    headers.add(IS_KOTLIN_CLIENT, "true");
    TenantContext.setFacilityNum(32899);
    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER),
            eq(DeliveryDocumentsSearchHandler.class));

    InstructionRequest manualInstructionRequest =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocuments();
    manualInstructionRequest.setManualReceivingEnabled(false);
    manualInstructionRequest.setFeatureType(AUTO_CASE_RECEIVE_FEATURE_TYPE);
    manualInstructionRequest.setDeliveryDocuments(null);

    List<DeliveryDocument> mockDeliveryDocuments =
        MockInstruction.getMultiPoLineDeliveryDocuments();
    List<DeliveryDocumentLine> deliveryDocumentLines =
        mockDeliveryDocuments.get(0).getDeliveryDocumentLines();
    mockDeliveryDocuments.get(0).setDeliveryDocumentLines(deliveryDocumentLines);

    DeliveryDocumentLine selectedDeliveryDocumentLine = deliveryDocumentLines.get(0);
    DeliveryDocument selectedDeliveryDocument = MockInstruction.getDeliveryDocuments().get(0);
    selectedDeliveryDocument.setDeliveryDocumentLines(
        Collections.singletonList(selectedDeliveryDocumentLine));

    doReturn(mockDeliveryDocuments)
        .when(defaultDeliveryDocumentsSearchHandler)
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));
    doReturn(new Pair<>(selectedDeliveryDocument, selectedDeliveryDocumentLine))
        .when(defaultDeliveryDocumentSelector)
        .autoSelectDeliveryDocumentLine(any());

    doReturn(
            MockInstructionResponse.getManualInstructionResponseForAutoCaseReceive(
                mockDeliveryDocuments))
        .when(manualInstructionService)
        .createManualInstruction(any(InstructionRequest.class), any(HttpHeaders.class));

    InstructionResponseImplNew instructionResponse =
        (InstructionResponseImplNew)
            instructionService.serveInstructionRequest(
                gson.toJson(manualInstructionRequest), headers);
    verify(defaultDeliveryDocumentsSearchHandler, times(1)).fetchDeliveryDocument(any(), any());
    verify(manualInstructionService, times(1)).createManualInstruction(any(), any());

    assertNotNull(instructionResponse.getDeliveryDocuments());
    assertNotNull(instructionResponse.getPrintJob());
    assertEquals(
        SCAN_TO_PRINT_INSTRUCTION_CODE, instructionResponse.getInstruction().getInstructionCode());
  }

  // XBlock Handler Test Cases
  @Test
  public void testServeInstruction_SinglePoLine_ItemXBlockedOn_KotlinOn()
      throws ReceivingException, IOException {
    String facilityNumVendorComplReq = "32899";
    String countryCodeVendorCompReq = "US";
    headers = MockHttpHeaders.getHeaders(facilityNumVendorComplReq, countryCodeVendorCompReq);
    headers.add(IS_KOTLIN_CLIENT, "true");
    TenantContext.setFacilityCountryCode(countryCodeVendorCompReq);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNumVendorComplReq));

    instructionRequest =
        MockInstruction.getInstructionRequestWithLithiumIonComplianceWithDocumentForSinglePO();

    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);

    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNumVendorComplReq,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);

    doReturn(MockInstruction.getDeliveryDocumentsForSinglePO())
        .when(defaultDeliveryDocumentsSearchHandler)
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));

    when(instructionPersisterService.fetchExistingOpenInstruction(
            any(DeliveryDocument.class), any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockInstruction.getInstruction());

    when(instructionPersisterService.fetchExistingInstructionIfexists(
            any(InstructionRequest.class)))
        .thenReturn(MockInstruction.getInstruction());

    doReturn(true).when(configUtils).isFeatureFlagEnabled(ReceivingConstants.ITEM_X_BLOCKED_VAL);

    try {
      when(instructionService.serveInstructionRequest(
              gson.toJson(MockInstruction.getInstructionRequestForPoCon()), headers))
          .thenThrow(
              new ReceivingBadDataException(
                  ExceptionCodes.ITEM_X_BLOCKED_ERROR,
                  String.format(
                      ReceivingConstants.X_BLOCK_ITEM_ERROR_MSG, deliveryDocumentLine.getItemNbr()),
                  String.valueOf(deliveryDocumentLine.getItemNbr())));
      fail();
    } catch (ReceivingBadDataException rbde) {
      assertEquals(rbde.getErrorCode(), ExceptionCodes.ITEM_X_BLOCKED_ERROR);
    } catch (ReceivingException e) {
      fail();
    }
  }

  @Test
  public void testServeInstruction_MultiPoLine_ItemXBlockedOn_KotlinOn()
      throws ReceivingException, IOException {
    String facilityNumVendorComplReq = "32899";
    String countryCodeVendorCompReq = "US";
    headers = MockHttpHeaders.getHeaders(facilityNumVendorComplReq, countryCodeVendorCompReq);
    headers.add(IS_KOTLIN_CLIENT, "true");
    TenantContext.setFacilityCountryCode(countryCodeVendorCompReq);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNumVendorComplReq));

    instructionRequest =
        MockInstruction.getInstructionRequestWithLithiumIonComplianceWithDocumentForMultiPO();

    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();

    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocuments.get(0).setDeliveryDocumentLines(deliveryDocumentLines);

    Pair<DeliveryDocument, DeliveryDocumentLine> selectedLine =
        new Pair<>(deliveryDocument, deliveryDocumentLine);
    doReturn(selectedLine)
        .when(defaultDeliveryDocumentSelector)
        .autoSelectDeliveryDocumentLine(anyList());

    assertEquals(2, deliveryDocuments.size());

    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNumVendorComplReq,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);

    doReturn(MockInstruction.getDeliveryDocumentsForMultiPO())
        .when(defaultDeliveryDocumentsSearchHandler)
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));

    when(instructionPersisterService.fetchExistingOpenInstruction(
            any(DeliveryDocument.class), any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockInstruction.getInstruction());

    when(instructionPersisterService.fetchExistingInstructionIfexists(
            any(InstructionRequest.class)))
        .thenReturn(MockInstruction.getInstruction());

    doReturn(true).when(configUtils).isFeatureFlagEnabled(ReceivingConstants.ITEM_X_BLOCKED_VAL);

    try {
      instructionService.serveInstructionRequest(
          gson.toJson(MockInstruction.getInstructionRequestForPoCon()), headers);
      fail();
    } catch (ReceivingBadDataException rbde) {
      assertEquals(rbde.getErrorCode(), ExceptionCodes.ITEM_X_BLOCKED_ERROR);
    } catch (ReceivingException e) {
      fail();
    }
  }

  @Test
  public void testServeInstruction_SinglePoLine_ItemXBlockedOff_KotlinOn()
      throws ReceivingException, IOException {
    String facilityNumVendorComplReq = "32899";
    String countryCodeVendorCompReq = "US";
    headers = MockHttpHeaders.getHeaders(facilityNumVendorComplReq, countryCodeVendorCompReq);
    headers.add(IS_KOTLIN_CLIENT, "true");
    TenantContext.setFacilityCountryCode(countryCodeVendorCompReq);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNumVendorComplReq));

    instructionRequest =
        MockInstruction.getInstructionRequestWithLithiumIonComplianceWithDocumentForSinglePO();

    List<DeliveryDocument> deliveryDocuments_gdm = instructionRequest.getDeliveryDocuments();

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments_gdm.get(0).getDeliveryDocumentLines().get(0);

    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNumVendorComplReq,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);

    doReturn(MockInstruction.getDeliveryDocumentsForSinglePO())
        .when(defaultDeliveryDocumentsSearchHandler)
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));

    when(instructionPersisterService.fetchExistingOpenInstruction(
            any(DeliveryDocument.class), any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockInstruction.getInstruction());

    when(instructionPersisterService.fetchExistingInstructionIfexists(
            any(InstructionRequest.class)))
        .thenReturn(MockInstruction.getInstruction());

    InstructionResponse instructionResponseFromVendorCompl =
        instructionService.serveInstructionRequest(
            gson.toJson(MockInstruction.getInstructionRequestForPoCon()), headers);

    assertNotNull(instructionResponseFromVendorCompl);
    assertNotNull(instructionResponseFromVendorCompl.getInstruction());
    assertEquals(1, instructionResponseFromVendorCompl.getDeliveryDocuments().size());
  }

  @Test
  public void testServeInstruction_MultiPoLine_ItemXBlockedOff_KotlinOn()
      throws ReceivingException, IOException {
    String facilityNumVendorComplReq = "32899";
    String countryCodeVendorCompReq = "US";
    headers = MockHttpHeaders.getHeaders(facilityNumVendorComplReq, countryCodeVendorCompReq);
    headers.add(IS_KOTLIN_CLIENT, "true");
    TenantContext.setFacilityCountryCode(countryCodeVendorCompReq);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNumVendorComplReq));

    instructionRequest =
        MockInstruction.getInstructionRequestWithLithiumIonComplianceWithDocumentForMultiPO();

    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();

    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocuments.get(0).setDeliveryDocumentLines(deliveryDocumentLines);

    Pair<DeliveryDocument, DeliveryDocumentLine> selectedLine =
        new Pair<>(deliveryDocument, deliveryDocumentLine);
    doReturn(selectedLine)
        .when(defaultDeliveryDocumentSelector)
        .autoSelectDeliveryDocumentLine(anyList());

    assertEquals(2, deliveryDocuments.size());

    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNumVendorComplReq,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);

    doReturn(MockInstruction.getDeliveryDocumentsForMultiPO())
        .when(defaultDeliveryDocumentsSearchHandler)
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));

    when(instructionPersisterService.fetchExistingOpenInstruction(
            any(DeliveryDocument.class), any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockInstruction.getInstruction());

    when(instructionPersisterService.fetchExistingInstructionIfexists(
            any(InstructionRequest.class)))
        .thenReturn(MockInstruction.getInstruction());

    InstructionResponse instructionResponseFromVendorCompl =
        instructionService.serveInstructionRequest(
            gson.toJson(MockInstruction.getInstructionRequestForPoCon()), headers);

    assertNotNull(instructionResponseFromVendorCompl);
    assertNotNull(instructionResponseFromVendorCompl.getInstruction());
    assertEquals(1, instructionResponseFromVendorCompl.getDeliveryDocuments().size());
  }

  @Test
  public void testServeInstructionResponseForPoCon_WithDeliveryDocs_XBlockedOn()
      throws ReceivingException {
    String facilityNumVendorComplReq = "32888";
    String countryCodeVendorCompReq = "US";
    headers = MockHttpHeaders.getHeaders(facilityNumVendorComplReq, countryCodeVendorCompReq);
    headers.add(IS_KOTLIN_CLIENT, "true");
    TenantContext.setFacilityCountryCode(countryCodeVendorCompReq);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNumVendorComplReq));
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.ITEM_X_BLOCKED_VAL))
        .thenReturn(Boolean.TRUE);
    try {
      InstructionRequest mockInstructionRequest = MockInstruction.getInstructionRequestForPoCon();
      DeliveryDocument deliveryDocument = new DeliveryDocument();
      DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
      deliveryDocumentLine.setPurchaseRefType("POCON");
      deliveryDocumentLine.setHandlingCode("X");
      List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
      deliveryDocumentLines.add(deliveryDocumentLine);
      deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
      List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
      deliveryDocuments.add(deliveryDocument);
      mockInstructionRequest.setDeliveryDocuments(deliveryDocuments);
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(mockInstructionRequest), headers);
      fail();
    } catch (ReceivingBadDataException exception) {
      assertEquals(exception.getErrorCode(), ExceptionCodes.ITEM_X_BLOCKED_ERROR);
    }
  }

  @Test
  public void testServeInstructionResponseForPoCon_WithoutDeliveryDocs_XBlockedOn()
      throws ReceivingException {
    String facilityNumVendorComplReq = "32888";
    String countryCodeVendorCompReq = "US";
    headers = MockHttpHeaders.getHeaders(facilityNumVendorComplReq, countryCodeVendorCompReq);
    headers.add(IS_KOTLIN_CLIENT, "true");
    TenantContext.setFacilityCountryCode(countryCodeVendorCompReq);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNumVendorComplReq));
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.ITEM_X_BLOCKED_VAL))
        .thenReturn(Boolean.TRUE);

    InstructionRequest mockInstructionRequest = MockInstruction.getInstructionRequestForPoCon();
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocument.setDeliveryStatus(DeliveryStatus.OPN);
    deliveryDocumentLine.setPurchaseRefType("POCON");
    deliveryDocumentLine.setHandlingCode("X");
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    deliveryDocuments.add(deliveryDocument);
    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER),
            eq(DeliveryDocumentsSearchHandler.class));
    when(deliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(gson.toJson(deliveryDocuments));
    try {
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(mockInstructionRequest), headers);
      fail();
    } catch (ReceivingBadDataException exception) {
      assertEquals(exception.getErrorCode(), ExceptionCodes.ITEM_X_BLOCKED_ERROR);
    }
  }

  @Test
  public void testPoConCheckForAutoCaseReceive() {
    try {
      instructionService.poConCheckForAutoCaseReceive(
          MockInstruction.getDeliveryDocumentsForPOCONFreight());
    } catch (ReceivingException receivingException) {
      assertEquals(
          receivingException.getErrorResponse().getErrorMessage(),
          GdmError.PO_CON_NOT_ALLOWED_FOR_AUTO_CASE_RECEIVE.getErrorMessage());
    }
  }

  @Test
  public void testValidateEligibilityForManualReceivingWhenFeatureTypeAutoCaseReceive() {
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    try {
      instructionService.validateEligibilityForManualReceiving(
          deliveryDocumentList, "AUTO_CASE_RECEIVE");
    } catch (ReceivingException receivingException) {
      assertEquals(
          receivingException.getErrorResponse().getErrorMessage(),
          GdmError.ITEM_NOT_CONVEYABLE_ERROR_FOR_AUTO_CASE_RECEIVE.getErrorMessage());
    }
  }

  @Test
  public void testValidateEligibilityForManualReceivingWhenFeatureTypeIsNotAutoCaseReceive() {
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    try {
      instructionService.validateEligibilityForManualReceiving(
          deliveryDocumentList, "TEST_FEATURE");
    } catch (ReceivingException receivingException) {
      assertEquals(
          receivingException.getErrorResponse().getErrorMessage(),
          GdmError.ITEM_NOT_CONVEYABLE_ERROR.getErrorMessage());
    }
  }

  @Test
  public void testValidateEligibilityForManualReceivingWhenFeatureTypeIsNullAutoCaseReceive() {
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    try {
      instructionService.validateEligibilityForManualReceiving(deliveryDocumentList, null);
    } catch (ReceivingException receivingException) {
      assertEquals(
          receivingException.getErrorResponse().getErrorMessage(),
          GdmError.ITEM_NOT_CONVEYABLE_ERROR.getErrorMessage());
    }
  }

  @Test
  public void testIsMoveDestBuFeatureFlagEnabledReturnsTrue() {
    Instruction instruction = MockInstruction.getInstruction();
    Container container = MockInstruction.getContainer();

    when(configUtils.isFeatureFlagEnabled(any())).thenReturn(Boolean.TRUE);
    Boolean retValue =
        instructionService.isMoveDestBuFeatureFlagEnabled(container, httpHeaders, instruction);
    assertEquals(retValue, Boolean.TRUE);
  }

  @Test
  public void testIsMoveDestBuFeatureFlagEnabledReturnsFalse() {
    Instruction instruction = MockInstruction.getInstruction();
    Container container = MockInstruction.getContainer();

    when(configUtils.isFeatureFlagEnabled(any())).thenReturn(Boolean.FALSE);
    Boolean retValue =
        instructionService.isMoveDestBuFeatureFlagEnabled(container, httpHeaders, instruction);
    assertEquals(retValue, Boolean.FALSE);
  }

  @Test
  public void testReceiveInstruction() throws ReceivingException {
    doReturn(defaultReceiveInstructionHandler)
        .when(configUtils)
        .getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.RECEIVE_INSTRUCTION_HANDLER_KEY),
            eq(ReceiveInstructionHandler.class));
    instructionService.receiveInstruction(
        1L, gson.toJson(getReceiveInstructionRequest()), httpHeaders);
    verify(defaultReceiveInstructionHandler, times(1)).receiveInstruction(any(), any(), any());
  }

  @Test
  public void testPublishWorkingIfNeeded_NoCCMConfigs() throws ReceivingException {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setDeliveryStatus(DeliveryStatus.OPN);
    deliveryDocument.setStateReasonCodes(
        Arrays.asList(
            DeliveryReasonCodeState.PENDING_DOCK_TAG.name(),
            DeliveryReasonCodeState.PENDING_PROBLEM.name()));
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    instructionResponse.setDeliveryDocuments(Collections.singletonList(deliveryDocument));
    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), anyList(), any()))
        .thenReturn(new DeliveryInfo());
    instructionService.publishWorkingIfNeeded(instructionResponse, httpHeaders);

    assertNotEquals(instructionResponse.getDeliveryStatus(), DeliveryStatus.WORKING.toString());
  }

  @Test
  public void testPublishWorkingIfNeeded_CCMConfigsEnabled() throws ReceivingException {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setDeliveryStatus(DeliveryStatus.OPN);
    deliveryDocument.setStateReasonCodes(
        Arrays.asList(
            DeliveryReasonCodeState.PENDING_DOCK_TAG.name(),
            DeliveryReasonCodeState.PENDING_PROBLEM.name()));
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    instructionResponse.setDeliveryDocuments(Collections.singletonList(deliveryDocument));
    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), anyList(), any()))
        .thenReturn(new DeliveryInfo());
    when(appConfig.getGdmDeliveryStateReasonCodesForOpenStatus())
        .thenReturn(
            Arrays.asList(
                DeliveryReasonCodeState.PENDING_DOCK_TAG.name(),
                DeliveryReasonCodeState.PENDING_PROBLEM.name(),
                DeliveryReasonCodeState.DELIVERY_REOPENED.name(),
                DeliveryReasonCodeState.READY_TO_RECEIVE.name(),
                DeliveryReasonCodeState.PENDING_AUDIT_TAG.name()));
    instructionService.publishWorkingIfNeeded(instructionResponse, httpHeaders);
    assertNotEquals(instructionResponse.getDeliveryStatus(), DeliveryStatus.WORKING.toString());
  }

  @Test
  public void testPublishWorkingIfNeeded_CCMConfigsEnabled_DOOR_OPEN() throws ReceivingException {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setDeliveryStatus(DeliveryStatus.OPN);
    deliveryDocument.setStateReasonCodes(Arrays.asList(DeliveryReasonCodeState.DOOR_OPEN.name()));
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    instructionResponse.setDeliveryDocuments(Collections.singletonList(deliveryDocument));
    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), anyList(), any()))
        .thenReturn(new DeliveryInfo());
    when(appConfig.getGdmDeliveryStateReasonCodesForOpenStatus())
        .thenReturn(
            Arrays.asList(
                DeliveryReasonCodeState.PENDING_DOCK_TAG.name(),
                DeliveryReasonCodeState.PENDING_PROBLEM.name(),
                DeliveryReasonCodeState.DELIVERY_REOPENED.name(),
                DeliveryReasonCodeState.READY_TO_RECEIVE.name(),
                DeliveryReasonCodeState.PENDING_AUDIT_TAG.name()));
    instructionService.publishWorkingIfNeeded(instructionResponse, httpHeaders);
    assertEquals(instructionResponse.getDeliveryStatus(), DeliveryStatus.WORKING.toString());
  }

  @Test
  public void testUpdateDeliveryStatusToWorking_CCMConfigsEnabled() throws ReceivingException {
    String facilityNumVendorComplReq = "32679";
    String countryCodeVendorCompReq = "US";
    headers = MockHttpHeaders.getHeaders(facilityNumVendorComplReq, countryCodeVendorCompReq);
    TenantContext.setFacilityCountryCode(countryCodeVendorCompReq);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNumVendorComplReq));
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setDeliveryStatus(DeliveryStatus.WORKING);
    deliveryDocument.setStateReasonCodes(
        Arrays.asList(
            DeliveryReasonCodeState.PENDING_DOCK_TAG.name(),
            DeliveryReasonCodeState.PENDING_PROBLEM.name()));
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    instructionResponse.setDeliveryDocuments(Collections.singletonList(deliveryDocument));
    GdmDeliveryStatusUpdateEvent gdmDeliveryStatusUpdateEvent = new GdmDeliveryStatusUpdateEvent();
    gdmDeliveryStatusUpdateEvent.setDeliveryNumber(43124232L);
    gdmDeliveryStatusUpdateEvent.setReceiverUserId("sysadmin");
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_DELIVERY_UPDATE_STATUS_ENABLED_BY_HTTP, false);
    when(deliveryService.updateDeliveryStatusToWorking(anyLong(), any(HttpHeaders.class)))
        .thenReturn(gdmDeliveryStatusUpdateEvent);

    instructionService.publishWorkingIfNeeded(instructionResponse, headers);
  }

  private ReceiveInstructionRequest getReceiveInstructionRequest() {
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDoorNumber("999");
    receiveInstructionRequest.setQuantity(1);
    receiveInstructionRequest.setQuantityUOM("ZA");
    receiveInstructionRequest.setReceiveBeyondThreshold(true);
    return receiveInstructionRequest;
  }

  @Test
  public void serveInstructionRequest_CheckSumValidateUpcNumber_SuccessTest()
      throws ReceivingException, IOException {
    String facilityNumVendorComplReq = "32888";
    String countryCodeVendorCompReq = "US";
    headers = MockHttpHeaders.getHeaders(facilityNumVendorComplReq, countryCodeVendorCompReq);
    TenantContext.setFacilityCountryCode(countryCodeVendorCompReq);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNumVendorComplReq));
    when(configUtils.isFeatureFlagEnabled(any())).thenReturn(Boolean.TRUE);
    List<DeliveryDocument> deliveryDocumentList =
        MockInstruction.getDeliveryDocumentsWithCountryCode();
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes(MockInstruction.getMockTransportationModeForLimitedQty());
    doReturn(deliveryDocumentList)
        .when(defaultDeliveryDocumentsSearchHandler)
        .fetchDeliveryDocument(ArgumentMatchers.any(), any(HttpHeaders.class));
    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNumVendorComplReq,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    doReturn(MockInstruction.getDeliveryDocumentsForMultiPO())
        .when(defaultDeliveryDocumentsSearchHandler)
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));
    when(instructionPersisterService.fetchExistingOpenInstruction(
            any(DeliveryDocument.class), any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockInstruction.getInstruction());
    when(instructionPersisterService.fetchExistingInstructionIfexists(
            any(InstructionRequest.class)))
        .thenReturn(MockInstruction.getInstruction());
    doReturn(false).when(configUtils).isFeatureFlagEnabled(ReceivingConstants.ITEM_X_BLOCKED_VAL);
    doReturn(false)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED);
    doReturn(false)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.IS_VALIDATE_PACK_TYPE_ACK_ENABLED);
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    instructionRequest.setUpcNumber("00799366522591");
    instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void serveInstructionRequest_CheckSumValidate_InvalidUPCNumber()
      throws ReceivingException, IOException {

    when(configUtils.isFeatureFlagEnabled(any())).thenReturn(Boolean.TRUE);
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    instructionRequest.setUpcNumber("123455");
    instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
  }

  @Test
  public void PO_SelectionWhenManualSelectionIsEnabled_fbqSelector()
      throws ReceivingException, IOException {
    String facilityNumVendorComplReq = "32899";
    String countryCodeVendorCompReq = "US";
    headers = MockHttpHeaders.getHeaders(facilityNumVendorComplReq, countryCodeVendorCompReq);
    headers.add(IS_KOTLIN_CLIENT, "true");
    TenantContext.setFacilityCountryCode(countryCodeVendorCompReq);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNumVendorComplReq));

    List<ReceiptSummaryEachesResponse> deliveryPoLineReceipts = new ArrayList<>();
    deliveryPoLineReceipts.add(new ReceiptSummaryEachesResponse("8888888886", 1, null, 0L));
    deliveryPoLineReceipts.add(new ReceiptSummaryEachesResponse("8888888887", 1, null, 0L));
    doReturn(deliveryPoLineReceipts)
        .when(receiptService)
        .receivedQtyByPoAndPoLinesAndDelivery(anyLong(), anyList(), anySet());
    List<ReceiptSummaryEachesResponse> poLineReceipts = new ArrayList<>();
    poLineReceipts.add(new ReceiptSummaryEachesResponse("8888888886", 1, null, 1L));
    poLineReceipts.add(new ReceiptSummaryEachesResponse("8888888887", 1, null, 0L));
    doReturn(poLineReceipts).when(receiptService).receivedQtyByPoAndPoLineList(anyList(), anySet());
    when(documentSelectorProvider.getDocumentSelector(any()))
        .thenReturn(fbqBasedDeliveryDocumentSelector);
    when(fbqBasedDeliveryDocumentSelector.getReceivedQtyByPoPol(any(), anyList(), anySet()))
        .thenReturn(
            receiptsAggregator.fromPOLandDPOLReceipts(poLineReceipts, deliveryPoLineReceipts));
    List<DeliveryDocument> pos = MockInstruction.getDeliveryDocumentsForMultiPO_for_manualPO();
    doReturn(new ImmutablePair<>(10L, 0L))
        .when(fbqBasedDeliveryDocumentSelector)
        .getOpenQtyTotalReceivedQtyForLineSelection(
            any(DeliveryDocument.class),
            any(DeliveryDocumentLine.class),
            any(ReceiptsAggregator.class),
            any(Boolean.class));
    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNumVendorComplReq,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    doReturn(MockInstruction.getDeliveryDocumentsForMultiPO_for_manualPO())
        .when(defaultDeliveryDocumentsSearchHandler)
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));

    when(instructionPersisterService.fetchExistingOpenInstruction(
            any(DeliveryDocument.class), any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(instructionPersisterService.fetchExistingInstructionIfexists(
            any(InstructionRequest.class)))
        .thenReturn(null);
    InstructionResponse instructionResponseFromVendorCompl =
        instructionService.serveInstructionRequest(
            gson.toJson(
                MockInstruction
                    .getInstructionRequestWithoutDeliveryDocumentWhenManualSelectionEnabled()),
            headers);
    assertNotNull(instructionResponseFromVendorCompl);
    assertNotNull(instructionResponseFromVendorCompl.getInstruction());
    assertTrue(
        instructionResponseFromVendorCompl
            .getInstruction()
            .getInstructionCode()
            .equals("MANUAL_PO_SELECTION"));
    assertEquals(2, instructionResponseFromVendorCompl.getDeliveryDocuments().size());
  }

  @Test
  public void PO_SelectionWhenManualSelectionIsEnabled_defaultSelector()
      throws ReceivingException, IOException {
    String facilityNumVendorComplReq = "32899";
    String countryCodeVendorCompReq = "US";
    headers = MockHttpHeaders.getHeaders(facilityNumVendorComplReq, countryCodeVendorCompReq);
    headers.add(IS_KOTLIN_CLIENT, "true");
    TenantContext.setFacilityCountryCode(countryCodeVendorCompReq);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNumVendorComplReq));

    List<ReceiptSummaryEachesResponse> deliveryPoLineReceipts = new ArrayList<>();
    deliveryPoLineReceipts.add(new ReceiptSummaryEachesResponse("8888888886", 1, null, 0L));
    deliveryPoLineReceipts.add(new ReceiptSummaryEachesResponse("8888888887", 1, null, 0L));
    doReturn(deliveryPoLineReceipts)
        .when(receiptService)
        .receivedQtyByPoAndPoLinesAndDelivery(anyLong(), anyList(), anySet());
    List<ReceiptSummaryEachesResponse> poLineReceipts = new ArrayList<>();
    poLineReceipts.add(new ReceiptSummaryEachesResponse("8888888886", 1, null, 1L));
    poLineReceipts.add(new ReceiptSummaryEachesResponse("8888888887", 1, null, 0L));
    doReturn(poLineReceipts).when(receiptService).receivedQtyByPoAndPoLineList(anyList(), anySet());
    when(documentSelectorProvider.getDocumentSelector(any()))
        .thenReturn(defaultDeliveryDocumentSelector);
    when(defaultDeliveryDocumentSelector.getReceivedQtyByPoPol(any(), anyList(), anySet()))
        .thenReturn(
            receiptsAggregator.fromPOLandDPOLReceipts(poLineReceipts, deliveryPoLineReceipts));
    List<DeliveryDocument> pos = MockInstruction.getDeliveryDocumentsForMultiPO_for_manualPO();
    doReturn(new ImmutablePair<>(10L, 0L))
        .when(defaultDeliveryDocumentSelector)
        .getOpenQtyTotalReceivedQtyForLineSelection(
            any(DeliveryDocument.class),
            any(DeliveryDocumentLine.class),
            any(ReceiptsAggregator.class),
            any(Boolean.class));
    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNumVendorComplReq,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    doReturn(MockInstruction.getDeliveryDocumentsForMultiPO_for_manualPO())
        .when(defaultDeliveryDocumentsSearchHandler)
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));

    when(instructionPersisterService.fetchExistingOpenInstruction(
            any(DeliveryDocument.class), any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(instructionPersisterService.fetchExistingInstructionIfexists(
            any(InstructionRequest.class)))
        .thenReturn(null);
    InstructionResponse instructionResponseFromVendorCompl =
        instructionService.serveInstructionRequest(
            gson.toJson(
                MockInstruction
                    .getInstructionRequestWithoutDeliveryDocumentWhenManualSelectionEnabled()),
            headers);
    assertNotNull(instructionResponseFromVendorCompl);
    assertNotNull(instructionResponseFromVendorCompl.getInstruction());
    assertTrue(
        instructionResponseFromVendorCompl
            .getInstruction()
            .getInstructionCode()
            .equals("MANUAL_PO_SELECTION"));
    assertEquals(2, instructionResponseFromVendorCompl.getDeliveryDocuments().size());
  }

  @Test
  public void PO_SelectionWhenManualSelectionIsEnabled_ovgInstruction()
      throws ReceivingException, IOException {
    String facilityNumVendorComplReq = "32899";
    String countryCodeVendorCompReq = "US";
    headers = MockHttpHeaders.getHeaders(facilityNumVendorComplReq, countryCodeVendorCompReq);
    headers.add(IS_KOTLIN_CLIENT, "true");
    TenantContext.setFacilityCountryCode(countryCodeVendorCompReq);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNumVendorComplReq));

    List<ReceiptSummaryEachesResponse> deliveryPoLineReceipts = new ArrayList<>();
    deliveryPoLineReceipts.add(new ReceiptSummaryEachesResponse("8888888886", 1, null, 0L));
    deliveryPoLineReceipts.add(new ReceiptSummaryEachesResponse("8888888887", 1, null, 10L));
    doReturn(deliveryPoLineReceipts)
        .when(receiptService)
        .receivedQtyByPoAndPoLinesAndDelivery(anyLong(), anyList(), anySet());
    List<ReceiptSummaryEachesResponse> poLineReceipts = new ArrayList<>();
    poLineReceipts.add(new ReceiptSummaryEachesResponse("8888888886", 1, null, 1L));
    poLineReceipts.add(new ReceiptSummaryEachesResponse("8888888887", 1, null, 10L));
    doReturn(poLineReceipts).when(receiptService).receivedQtyByPoAndPoLineList(anyList(), anySet());
    when(documentSelectorProvider.getDocumentSelector(any()))
        .thenReturn(fbqBasedDeliveryDocumentSelector);
    when(fbqBasedDeliveryDocumentSelector.getReceivedQtyByPoPol(any(), anyList(), anySet()))
        .thenReturn(
            receiptsAggregator.fromPOLandDPOLReceipts(poLineReceipts, deliveryPoLineReceipts));
    List<DeliveryDocument> pos = MockInstruction.getDeliveryDocumentsForMultiPO_for_manualPO();
    doReturn(new ImmutablePair<>(0L, 10L))
        .when(fbqBasedDeliveryDocumentSelector)
        .getOpenQtyTotalReceivedQtyForLineSelection(
            any(DeliveryDocument.class),
            any(DeliveryDocumentLine.class),
            any(ReceiptsAggregator.class),
            any(Boolean.class));
    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNumVendorComplReq,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    doReturn(MockInstruction.getDeliveryDocumentsForMultiPO_for_manualPO())
        .when(defaultDeliveryDocumentsSearchHandler)
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));

    when(instructionPersisterService.fetchExistingOpenInstruction(
            any(DeliveryDocument.class), any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(instructionPersisterService.fetchExistingInstructionIfexists(
            any(InstructionRequest.class)))
        .thenReturn(null);
    InstructionResponse instructionResponseFromVendorCompl =
        instructionService.serveInstructionRequest(
            gson.toJson(
                MockInstruction
                    .getInstructionRequestWithoutDeliveryDocumentWhenManualSelectionEnabled()),
            headers);
    assertNotNull(instructionResponseFromVendorCompl);
    assertNotNull(instructionResponseFromVendorCompl.getInstruction());
    assertTrue(
        instructionResponseFromVendorCompl
            .getInstruction()
            .getInstructionCode()
            .equals("CCOveragePallet"));
    assertEquals(1, instructionResponseFromVendorCompl.getDeliveryDocuments().size());
  }

  @Test
  public void PrimeSlotCheckForImport_PrimeslotFound() throws ReceivingException, IOException {
    String facilityNumVendorComplReq = "6060";
    String countryCodeVendorCompReq = "US";
    headers = MockHttpHeaders.getHeaders(facilityNumVendorComplReq, countryCodeVendorCompReq);
    headers.add(IS_KOTLIN_CLIENT, "true");
    TenantContext.setFacilityCountryCode(countryCodeVendorCompReq);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNumVendorComplReq));
    doReturn(true).when(importsInstructionUtils).isStorageTypePo(any());
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocuments_AtlasItems();
    DeliveryDocument DeliveryDocument = instructionRequest.getDeliveryDocuments().get(0);
    instructionRequest.setManualReceivingEnabled(false);
    instructionRequest.setMessageId("a1-b1-c1");
    SlottingDivertLocations location = new SlottingDivertLocations();
    location.setType("success");
    location.setLocation("A1234");
    location.setItemNbr(123456);
    List<SlottingDivertLocations> locationList = new ArrayList();
    locationList.add(location);
    SlottingPalletResponse mockSlottingResponseBody = new SlottingPalletResponse();
    mockSlottingResponseBody.setMessageId("a1-b1-c1");
    mockSlottingResponseBody.setLocations(locationList);
    doReturn(mockSlottingResponseBody)
        .when(importSlottingService)
        .getPrimeSlot(anyString(), anyList(), anyInt(), any());
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.IS_STORAGE_CHECK_ENABLED))
        .thenReturn(true);
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(
            getJSONFromPath("../receiving-test/src/main/resources/json/SSTKOFResponse.json"));
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(MockInstruction.getInstruction());
    when(configUtils.getConfiguredInstance(
            "6060", ReceivingConstants.INSTRUCTION_PUBLISHER, MessagePublisher.class))
        .thenReturn(messagePublisher);
    when(configUtils.getConfiguredInstance(
            "6060", ReceivingConstants.INSTRUCTION_PUBLISHER, MessagePublisher.class))
        .thenReturn(messagePublisher);
    doNothing().when(messagePublisher).publish(any(), any());
    instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
  }

  @Test
  public void PrimeSlotCheckForImport_PrimeslotNotFound() throws ReceivingException, IOException {
    String facilityNumVendorComplReq = "6060";
    String countryCodeVendorCompReq = "US";
    headers = MockHttpHeaders.getHeaders(facilityNumVendorComplReq, countryCodeVendorCompReq);
    headers.add(IS_KOTLIN_CLIENT, "true");
    TenantContext.setFacilityCountryCode(countryCodeVendorCompReq);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNumVendorComplReq));
    doReturn(true).when(importsInstructionUtils).isStorageTypePo(any());
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocuments_AtlasItems();
    DeliveryDocument DeliveryDocument = instructionRequest.getDeliveryDocuments().get(0);
    instructionRequest.setManualReceivingEnabled(false);
    instructionRequest.setMessageId("a1-b1-c1");
    SlottingDivertLocations location = new SlottingDivertLocations();
    location.setType("error");
    location.setCode("GLS-SMART-SLOTING-4040009");
    location.setItemNbr(123456);
    List<SlottingDivertLocations> locationList = new ArrayList();
    locationList.add(location);
    SlottingPalletResponse mockSlottingResponseBody = new SlottingPalletResponse();
    mockSlottingResponseBody.setMessageId("a1-b1-c1");
    mockSlottingResponseBody.setLocations(locationList);
    doReturn(mockSlottingResponseBody)
        .when(importSlottingService)
        .getPrimeSlot(anyString(), anyList(), anyInt(), any());
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.IS_STORAGE_CHECK_ENABLED))
        .thenReturn(true);
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(
            getJSONFromPath("../receiving-test/src/main/resources/json/SSTKOFResponse.json"));
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(MockInstruction.getInstruction());
    when(configUtils.getConfiguredInstance(
            "6060", ReceivingConstants.INSTRUCTION_PUBLISHER, MessagePublisher.class))
        .thenReturn(messagePublisher);
    when(configUtils.getConfiguredInstance(
            "6060", ReceivingConstants.INSTRUCTION_PUBLISHER, MessagePublisher.class))
        .thenReturn(messagePublisher);
    doNothing().when(messagePublisher).publish(any(), any());
    try {
      instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    } catch (ReceivingException e) {
      assert (true);
    }
  }

  @Test
  public void testCompleteInstruction_RoboDepal_FloorLineDockTag()
      throws ReceivingException, GDMRestApiClientException {
    String facilityNum = "32888";
    String facilityCountryCode = "US";
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    TenantContext.setFacilityCountryCode("US");
    headers = MockHttpHeaders.getHeaders(facilityNum, facilityCountryCode);
    Instruction instruction = MockInstruction.getInstruction();
    instruction.setDockTagId("a32612000000000001");
    instruction.setActivityName(DOCK_TAG);
    CompleteInstructionRequest instructionRequest = new CompleteInstructionRequest();
    instructionRequest.setSkuIndicator("MULTI");
    instructionRequest.setDoorNumber("123");
    DockTag dockTag = DockTag.builder().dockTagType(DockTagType.FLOOR_LINE).build();
    Delivery delivery = Delivery.builder().priority(3).build();
    Map<String, Object> instructionContainerMap = new HashMap<>();
    instructionContainerMap.put("instruction", instruction);
    instructionContainerMap.put("container", MockContainer.getContainer());
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);
    when(configUtils.isFeatureFlagEnabled(ROBO_DEPAL_FEATURE_ENABLED)).thenReturn(true);
    when(dockTagPersisterService.getDockTagByDockTagId(anyString())).thenReturn(dockTag);
    when(gdmRestApiClient.getDeliveryWithDeliveryResponse(anyLong(), any())).thenReturn(delivery);
    when(configUtils.getConfiguredInstance(
            facilityNum, DOCKTAG_INFO_PUBLISHER, MessagePublisher.class))
        .thenReturn(messagePublisher);
    when(configUtils.getConfiguredInstance(
            facilityNum, INSTRUCTION_PUBLISHER, MessagePublisher.class))
        .thenReturn(messagePublisher);
    doNothing().when(messagePublisher).publish(any(), any());
    when(instructionPersisterService.completeAndCreatePrintJob(eq(headers), any(Instruction.class)))
        .thenReturn(instructionContainerMap);
    when(configUtils.isFeatureFlagEnabled(DISABLE_DOCK_TAG_CONTAINER_PUBLISH)).thenReturn(true);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    InstructionResponse instructionResponse =
        instructionService.completeInstruction(1901L, instructionRequest, headers);
    assertNotNull(instructionResponse);
  }

  @Test
  public void testCompleteInstruction_RoboDepal()
      throws ReceivingException, GDMRestApiClientException {
    String facilityNum = "32888";
    String facilityCountryCode = "US";
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    TenantContext.setFacilityCountryCode("US");
    headers = MockHttpHeaders.getHeaders(facilityNum, facilityCountryCode);
    Instruction instruction = MockInstruction.getInstruction();
    instruction.setActivityName(DOCK_TAG);
    CompleteInstructionRequest instructionRequest = new CompleteInstructionRequest();
    instructionRequest.setSkuIndicator("MULTI");
    instructionRequest.setDoorNumber("123");
    Delivery delivery = Delivery.builder().priority(3).build();
    Map<String, Object> instructionContainerMap = new HashMap<>();
    instructionContainerMap.put("instruction", instruction);
    instructionContainerMap.put("container", MockContainer.getContainer());
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);
    when(configUtils.isFeatureFlagEnabled(ROBO_DEPAL_FEATURE_ENABLED)).thenReturn(true);
    when(gdmRestApiClient.getDeliveryWithDeliveryResponse(anyLong(), any())).thenReturn(delivery);
    when(configUtils.getConfiguredInstance(
            facilityNum, DOCKTAG_INFO_PUBLISHER, MessagePublisher.class))
        .thenReturn(messagePublisher);
    when(configUtils.getConfiguredInstance(
            facilityNum, INSTRUCTION_PUBLISHER, MessagePublisher.class))
        .thenReturn(messagePublisher);
    doNothing().when(messagePublisher).publish(any(), any());
    when(instructionPersisterService.completeAndCreatePrintJob(eq(headers), any(Instruction.class)))
        .thenReturn(instructionContainerMap);
    when(configUtils.isFeatureFlagEnabled(DISABLE_DOCK_TAG_CONTAINER_PUBLISH)).thenReturn(true);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    InstructionResponse instructionResponse =
        instructionService.completeInstruction(1901L, instructionRequest, headers);
    assertNotNull(instructionResponse);
  }
}
