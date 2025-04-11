package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.common.validators.DeliveryValidator;
import com.walmart.move.nim.receiving.core.common.validators.PurchaseReferenceValidator;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.DeliveryItemOverride;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.factory.DocumentSelectorProvider;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.helper.ProblemReceivingHelper;
import com.walmart.move.nim.receiving.core.item.rules.LimitedQtyRule;
import com.walmart.move.nim.receiving.core.item.rules.LithiumIonLimitedQtyRule;
import com.walmart.move.nim.receiving.core.item.rules.LithiumIonRule;
import com.walmart.move.nim.receiving.core.item.rules.RuleSet;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.message.publisher.JMSInstructionPublisher;
import com.walmart.move.nim.receiving.core.message.publisher.JMSReceiptPublisher;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaInstructionMessagePublisher;
import com.walmart.move.nim.receiving.core.message.publisher.MessagePublisher;
import com.walmart.move.nim.receiving.core.mock.data.MockGdmResponse;
import com.walmart.move.nim.receiving.core.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliveryDocumentResponse;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.repositories.ReceiptCustomRepository;
import com.walmart.move.nim.receiving.core.repositories.ReceiptRepository;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import org.mockito.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class CreateInstructionServiceTest extends ReceivingTestBase {
  @InjectMocks private InstructionService instructionService;
  @InjectMocks private InstructionPersisterService instructionPersisterService;
  @InjectMocks @Spy private DeliveryDocumentHelper deliveryDocumentHelper;
  @InjectMocks private ManualInstructionService manualInstructionService;
  @InjectMocks private InstructionHelperService instructionHelperService;
  @InjectMocks private JMSInstructionPublisher jmsInstructionPublisher;
  @InjectMocks private ContainerService containerService;
  @InjectMocks private RegulatedItemService regulatedItemService;
  @Spy private InstructionService instructionServiceSpied;
  @Mock private AppConfig appConfig;
  @Mock private FdeService fdeService;
  @Mock private MovePublisher movePublisher;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private JmsPublisher jmsPublisher;
  @Mock private DCFinService dcFinService;
  @Mock private AsyncPersister asyncPersister;
  @Mock private ReceiptService receiptService;
  @Mock private PrintJobService printJobService;
  @Mock private DeliveryServiceImpl deliveryService;
  @Mock private DeliveryService baseDeliveryService;
  @Mock private ReceiptRepository receiptRepository;
  @Mock private InstructionRepository instructionRepository;
  @Mock private DeliveryStatusPublisher deliveryStatusPublisher;
  @Mock private ReceiptCustomRepository receiptCustomRepository;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private DeliveryItemOverrideService deliveryItemOverrideService;
  @Mock private DeliveryValidator deliveryValidator;
  @Mock private PurchaseReferenceValidator purchaseReferenceValidator;
  @Mock private DefaultLabelIdProcessor defaultLabelIdProcessor;
  @Mock private ProblemService problemService;
  @Mock private ProblemReceivingHelper problemReceivingHelper;
  @Mock private DefaultSorterPublisher defaultSorterPublisher;
  @Mock private DefaultDeliveryDocumentSelector defaultDeliveryDocumentSelector;
  @Mock private JMSReceiptPublisher JMSReceiptPublisher;
  @Mock private KafkaInstructionMessagePublisher kafkaInstructionMessagePublisher;
  // TODO: remove once non static method removed from this utils class
  @Mock private InstructionUtils instructionUtils;
  @Mock private ReceiptPublisher receiptPublisher;
  @Mock private PrintLabelHelper printLabelHelper;
  @InjectMocks @Spy DocumentSelectorProvider documentSelectorProvider;

  @InjectMocks @Spy private DefaultOpenQtyCalculator defaultOpenQtyCalculator;

  @InjectMocks @Spy
  private DefaultDeliveryDocumentsSearchHandler defaultDeliveryDocumentsSearchHandler;

  private InstructionError instructionError;
  private Gson gson = new Gson();
  private Instruction completedInstruction;
  private Instruction instructionByMessageId;
  private HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
  private Container container = MockInstruction.getContainer();
  private Instruction pendingInstruction = MockInstruction.getPendingInstruction();
  private List<DeliveryDocument> deliveryDocuments = MockInstruction.getDeliveryDocuments();
  private InstructionRequest instructionRequest =
      MockInstruction.getInstructionRequestWithOpenState();
  private InstructionRequest instructionReqWithWorkingState =
      MockInstruction.getInstructionRequestWithWorkingState();
  private FdeCreateContainerResponse fdeCreateContainerResponse =
      MockInstruction.getFdeCreateContainerResponse();
  private FdeCreateContainerResponse asnFdeCreateContainerResponse =
      MockInstruction.getAsnFdeCreateContainerResponse();
  private ShipmentResponseData gdmContainerResponse =
      MockGdmResponse.getGdmContainerResponseWithChilds();
  private ShipmentResponseData gdmContainerResponse1 = MockGdmResponse.getGdmContainerResponse();
  private GdmDeliveryDocumentResponse gdmDeliveryDocumentRespose =
      MockGdmResponse.getGdmDeliveryDocumentResponse();
  private JsonObject defaultFeatureFlagsByFacility = new JsonObject();
  private DeliveryDtls gdmDeliveryDocumentResponseForPoCon =
      MockGdmResponse.getGdmDeliveryDocumentResponseForPoCon();
  private DeliveryDtls gdmDeliveryDocumentResponseForPoConWithUnknownChannelMethod =
      MockGdmResponse.getGdmDeliveryDocumentResponseForPoConwithUnkownChannelMethod();
  private InstructionRequest poConInstructionRequest =
      MockInstruction.getInstructionRequestForPoCon();

  private InstructionRequest poConInstructionRequestWithDeliveryDocuments =
      MockInstruction.getInstructionRequestWithDeliveryDocumentsForPoCon();

  private GdmError gdmError;

  private InstructionResponse instructionResponse;
  private LithiumIonRule lithiumIon = new LithiumIonRule();
  private LimitedQtyRule limitedQty = new LimitedQtyRule();
  private LithiumIonLimitedQtyRule lithiumIonLimitedQty = new LithiumIonLimitedQtyRule();
  private RuleSet itemCategoryRuleSet = new RuleSet(lithiumIonLimitedQty, lithiumIon, limitedQty);

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(instructionService, "containerService", containerService);
    ReflectionTestUtils.setField(
        instructionService, "purchaseReferenceValidator", purchaseReferenceValidator);
    ReflectionTestUtils.setField(instructionPersisterService, "containerService", containerService);
    ReflectionTestUtils.setField(instructionHelperService, "containerService", containerService);
    ReflectionTestUtils.setField(
        instructionService, "instructionPersisterService", instructionPersisterService);
    ReflectionTestUtils.setField(
        instructionService, "manualInstructionService", manualInstructionService);
    ReflectionTestUtils.setField(
        instructionService, "instructionHelperService", instructionHelperService);
    ReflectionTestUtils.setField(
        manualInstructionService, "instructionHelperService", instructionHelperService);
    ReflectionTestUtils.setField(
        manualInstructionService, "deliveryDocumentHelper", deliveryDocumentHelper);
    ReflectionTestUtils.setField(
        manualInstructionService, "instructionPersisterService", instructionPersisterService);
    ReflectionTestUtils.setField(
        manualInstructionService, "tenantSpecificConfigReader", configUtils);
    ReflectionTestUtils.setField(instructionService, "gson", gson);
    ReflectionTestUtils.setField(defaultDeliveryDocumentsSearchHandler, "gson", gson);
    ReflectionTestUtils.setField(containerService, "gson", gson);
    ReflectionTestUtils.setField(instructionServiceSpied, "configUtils", configUtils);
    ReflectionTestUtils.setField(instructionService, "itemCategoryRuleSet", itemCategoryRuleSet);
    ReflectionTestUtils.setField(instructionService, "regulatedItemService", regulatedItemService);
    ReflectionTestUtils.setField(regulatedItemService, "itemCategoryRuleSet", itemCategoryRuleSet);
    ReflectionTestUtils.setField(instructionService, "tenantSpecificConfigReader", configUtils);
    try {

      Gson gsonWithDateFormat = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").create();
      String dataPath =
          new File("../receiving-test/src/main/resources/json/instruction_response.json")
              .getCanonicalPath();
      instructionResponse =
          gsonWithDateFormat.fromJson(
              new String(Files.readAllBytes(Paths.get(dataPath))),
              InstructionResponseImplNew.class);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Test data which needed to set up before each test. Test data which gets modified at each tests
   * and requires back to original state before next test case execute. So that we can re use the
   * same data.
   *
   * @throws ReceivingException
   */
  @BeforeMethod
  public void setUpTestDataBeforeEachTest() throws ReceivingException {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32987);
    DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_ISO8601);
    TenantContext.setAdditionalParams(CONTAINER_CREATE_TS, dateFormat.format(new Date()));
    instructionByMessageId = MockInstruction.getInstructionForMessageId();
    completedInstruction = MockInstruction.getCompleteInstruction();

    // Mocking GDM call
    List<DeliveryDocument> deliveryDocuments1 = MockInstruction.getDeliveryDocuments();
    GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
    List<DeliveryDocument> oneDeliveryDocuments = new ArrayList<>();
    oneDeliveryDocuments.add(deliveryDocuments1.get(0));
    gdmDeliveryDocumentResponse.setDeliveryDocuments(oneDeliveryDocuments);
    doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
        .when(deliveryService)
        .findDeliveryDocument(anyLong(), anyString(), any());
    doReturn(0L).when(receiptService).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    doReturn(10).when(appConfig).getMaxAllowedLabelsAtOnce();
    when(configUtils.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.INSTRUCTION_PUBLISHER,
            MessagePublisher.class))
        .thenReturn(jmsInstructionPublisher);
    when(configUtils.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.SORTER_PUBLISHER,
            SorterPublisher.class))
        .thenReturn(defaultSorterPublisher);
    when(configUtils.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_SERVICE_KEY), any()))
        .thenReturn(deliveryService);
    when(configUtils.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any()))
        .thenReturn(defaultDeliveryDocumentsSearchHandler);
    when(configUtils.getConfiguredInstance(
            "32987", ReceivingConstants.RECEIPT_EVENT_HANDLER, MessagePublisher.class))
        .thenReturn(JMSReceiptPublisher);
    when(configUtils.getConfiguredInstance(
            anyString(),
            eq(OPEN_QTY_CALCULATOR),
            eq(DEFAULT_OPEN_QTY_CALCULATOR),
            eq(OpenQtyCalculator.class)))
        .thenReturn(defaultOpenQtyCalculator);
    when(configUtils.getConfiguredInstance(
            anyString(),
            eq(DELIVERY_DOCUMENT_SELECTOR),
            eq(DEFAULT_DELIVERY_DOCUMENT_SELECTOR),
            eq(DeliveryDocumentSelector.class)))
        .thenReturn(defaultDeliveryDocumentSelector);
  }

  @AfterMethod
  public void tearDown() {
    reset(configUtils);
    reset(movePublisher);
    reset(deliveryItemOverrideService);
    reset(deliveryStatusPublisher);
    reset(receiptService);
    reset(problemReceivingHelper);
    reset(defaultSorterPublisher);
    reset(defaultDeliveryDocumentSelector);
    reset(problemService);
    reset(deliveryDocumentHelper);
    reset(deliveryService);
    reset(baseDeliveryService);
  }

  @Test
  public void testGetInstructionById() throws ReceivingException {
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(completedInstruction));

    Instruction instructionResponse =
        instructionPersisterService.getInstructionById(Long.valueOf("21119003"));

    assertEquals(instructionResponse, completedInstruction);
  }

  @Test
  public void testServeInstructionRequest_whenUpcIsEmpty() {
    instructionError = InstructionErrorCode.getErrorValue(ReceivingException.NO_UPC_ERROR);
    try {
      when(configUtils.getFeatureFlagsByFacility("32987"))
          .thenReturn(defaultFeatureFlagsByFacility);
      instructionRequest.setUpcNumber("");
      instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    } catch (ReceivingException receivingException) {
      assert (true);
      assertEquals(
          receivingException.getErrorResponse().getErrorMessage(),
          instructionError.getErrorMessage());
    }
  }

  @Test
  public void testServeInstructionRequest_success() throws ReceivingException {
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    ReflectionTestUtils.setField(instructionServiceSpied, "gson", gson);

    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    when(deliveryItemOverrideService.findByDeliveryNumberAndItemNumber(
            Long.parseLong(instructionRequest.getDeliveryNumber()),
            instructionRequest
                .getDeliveryDocuments()
                .get(0)
                .getDeliveryDocumentLines()
                .get(0)
                .getItemNbr()))
        .thenReturn(null);
    doReturn(0l)
        .when(instructionRepository)
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine(anyString(), anyInt());
    doReturn(1l).when(receiptService).getReceivedQtyByPoAndPoLine("4763030227", 1);
    when(instructionRepository.save(any())).thenReturn(pendingInstruction);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));

    try {
      doReturn(pendingInstruction)
          .when(instructionServiceSpied)
          .createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test
  public void testServeInstructionRequest_whenUpcInRequestAndHasOneDeliveryDocumentInGDM() {
    List<DeliveryDocument> deliveryDocuments1 = MockInstruction.getDeliveryDocuments();
    reset(deliveryService);
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    ReflectionTestUtils.setField(instructionServiceSpied, "gson", gson);
    doReturn(1l).when(receiptService).getReceivedQtyByPoAndPoLine("4763030227", 1);
    when(instructionRepository.findByMessageId(any())).thenReturn(null);
    when(instructionRepository.save(any())).thenReturn(pendingInstruction);

    try {
      doReturn(defaultFeatureFlagsByFacility).when(configUtils).getFeatureFlagsByFacility("32987");
      GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
      List<DeliveryDocument> oneDeliveryDocuments = new ArrayList<>();
      oneDeliveryDocuments.add(deliveryDocuments1.get(0));
      gdmDeliveryDocumentResponse.setDeliveryDocuments(oneDeliveryDocuments);
      when(deliveryItemOverrideService.findByDeliveryNumberAndItemNumber(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              gdmDeliveryDocumentResponse
                  .getDeliveryDocuments()
                  .get(0)
                  .getDeliveryDocumentLines()
                  .get(0)
                  .getItemNbr()))
          .thenReturn(Optional.empty());
      doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
          .when(deliveryService)
          .findDeliveryDocument(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              instructionRequest.getUpcNumber(),
              httpHeaders);
      doReturn(gson.toJson(fdeCreateContainerResponse)).when(fdeService).receive(any(), any());
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getGtin(),
          instructionRequest.getUpcNumber());
      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getFirstExpiryFirstOut(),
          Boolean.FALSE);
      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getWarehouseMinLifeRemainingToReceive(),
          deliveryDocuments1
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getWarehouseMinLifeRemainingToReceive());
      assert (true);
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test
  public void
      testServeInstructionRequest_whenUpcInRequestAndHasOneDeliveryDocumentInGDM_withLithiumItem() {
    List<DeliveryDocument> deliveryDocuments1 = MockInstruction.getDeliveryDocuments();
    TransportationModes transportationModes = new TransportationModes();
    Mode mode = new Mode();
    mode.setCode(1);
    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("N/A");
    transportationModes.setDotHazardousClass(dotHazardousClass);
    transportationModes.setProperShipping("Lithium Ion Battery Packed with Equipment");
    transportationModes.setPkgInstruction(Arrays.asList("965", "966"));
    transportationModes.setMode(mode);

    deliveryDocuments1
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes(Collections.singletonList(transportationModes));

    reset(deliveryService);
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    ReflectionTestUtils.setField(instructionServiceSpied, "gson", gson);
    doReturn(1l).when(receiptService).getReceivedQtyByPoAndPoLine("4763030227", 1);
    when(instructionRepository.findByMessageId(any())).thenReturn(null);

    try {
      doReturn(defaultFeatureFlagsByFacility).when(configUtils).getFeatureFlagsByFacility("32987");
      GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
      List<DeliveryDocument> oneDeliveryDocuments = new ArrayList<>();
      oneDeliveryDocuments.add(deliveryDocuments1.get(0));
      gdmDeliveryDocumentResponse.setDeliveryDocuments(oneDeliveryDocuments);
      doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
          .when(deliveryService)
          .findDeliveryDocument(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              instructionRequest.getUpcNumber(),
              httpHeaders);
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertNull(instructionResponse.getInstruction());
      assert (true);
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test
  public void testServeInstructionRequest_whenAsnInRequest() {
    reset(instructionRepository);
    reset(receiptService);
    reset(deliveryService);
    reset(printJobService);
    reset(jmsPublisher);
    reset(JMSReceiptPublisher);
    List<DeliveryDocument> deliveryDocuments1 = MockInstruction.getDeliveryDocuments();

    /*doReturn(deliveryService)
    .when(configUtils)
    .getConfiguredInstance(
        "32987", ReceivingConstants.DELIVERY_SERVICE_KEY, DeliveryService.class);*/
    instructionRequest.setAsnBarcode("00100077672010660414");
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    ReflectionTestUtils.setField(instructionServiceSpied, "gson", gson);

    try {
      doNothing()
          .when(movePublisher)
          .publishMove(
              anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
      doReturn(defaultFeatureFlagsByFacility).when(configUtils).getFeatureFlagsByFacility("32987");
      doReturn(1l).when(receiptService).getReceivedQtyByPoAndPoLine("4763030227", 1);

      when(configUtils.isFeatureFlagEnabled(ReceivingConstants.MOVE_DEST_BU_ENABLED))
          .thenReturn(false);
      when(containerPersisterService.getContainerDetails("00000077670099006775")).thenReturn(null);
      doNothing()
          .when(jmsPublisher)
          .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
      instructionRequest.setAsnBarcode("00000077670099006775");
      doReturn(gdmContainerResponse)
          .when(deliveryService)
          .getContainerInfoByAsnBarcode("00000077670099006775", httpHeaders);

      when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
          .thenReturn(gson.toJson(asnFdeCreateContainerResponse));
      doReturn(
              InstructionUtils.processInstructionResponseForS2S(
                  instructionRequest, asnFdeCreateContainerResponse, 1, httpHeaders))
          .when(instructionRepository)
          .save(any());
      GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
      List<DeliveryDocument> oneDeliveryDocuments = new ArrayList<>();
      oneDeliveryDocuments.add(deliveryDocuments1.get(0));
      gdmDeliveryDocumentResponse.setDeliveryDocuments(oneDeliveryDocuments);
      doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
          .when(deliveryService)
          .findDeliveryDocument(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              instructionRequest.getUpcNumber(),
              httpHeaders);

      doNothing()
          .when(dcFinService)
          .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));

      when(configUtils.getConfiguredInstance(
              "32987", ReceivingConstants.RECEIPT_EVENT_HANDLER, MessagePublisher.class))
          .thenReturn(JMSReceiptPublisher);
      doNothing().when(JMSReceiptPublisher).publish(any(), any());
      instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (true);
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test
  public void testServeInstructionRequest_whenUpcInRequestAndHasNoDeliveryDocumentInGDM() {
    reset(instructionServiceSpied);
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    ReflectionTestUtils.setField(instructionServiceSpied, "gson", gson);
    try {
      doReturn(defaultFeatureFlagsByFacility).when(configUtils).getFeatureFlagsByFacility("32987");
      GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
      gdmDeliveryDocumentResponse.setDeliveryDocuments(deliveryDocuments);
      doThrow(
              new ReceivingException(
                  "No po/poline information found", HttpStatus.NOT_FOUND, "searchDocument"))
          .when(deliveryService)
          .findDeliveryDocument(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              instructionRequest.getUpcNumber(),
              httpHeaders);
      instructionServiceSpied.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    } catch (ReceivingException receivingException) {
      try {
        verify(instructionServiceSpied, times(0))
            .createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      } catch (ReceivingException innerReceivingException) {
        assert (false);
      }
    }
  }

  @Test
  public void testServeInstructionRequest_whenUpcInRequestAndHasEmptyDeliveryDocumentInGDM() {
    reset(instructionServiceSpied);
    reset(deliveryService);
    instructionRequest.setAsnBarcode("");
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    ReflectionTestUtils.setField(instructionServiceSpied, "gson", gson);
    ReflectionTestUtils.setField(
        instructionServiceSpied, "instructionRepository", instructionRepository);
    try {
      doReturn(defaultFeatureFlagsByFacility).when(configUtils).getFeatureFlagsByFacility("32987");
      GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
      List<DeliveryDocument> oneDeliveryDocuments = new ArrayList<>();
      gdmDeliveryDocumentResponse.setDeliveryDocuments(oneDeliveryDocuments);
      doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
          .when(deliveryService)
          .findDeliveryDocument(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              instructionRequest.getUpcNumber(),
              httpHeaders);
      doReturn(null)
          .when(instructionServiceSpied)
          .createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      doReturn(null).when(instructionRepository).findByMessageId(anyString());
      instructionServiceSpied.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);

    } catch (ReceivingException receivingException) {
      assert (true);
    }
  }

  @Test
  public void testServeInstructionRequest_whenUpcInRequestAndHasEmptyDeliveryDocLinesInGDM() {
    instructionRequest.setAsnBarcode("");
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    ReflectionTestUtils.setField(instructionService, "gson", gson);
    ReflectionTestUtils.setField(instructionService, "deliveryValidator", deliveryValidator);
    ReflectionTestUtils.setField(
        instructionService, "instructionRepository", instructionRepository);
    try {
      when(configUtils.getFeatureFlagsByFacility("32987"))
          .thenReturn(defaultFeatureFlagsByFacility);
      GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
      List<DeliveryDocument> oneDeliveryDocuments = new ArrayList<>();
      List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
      deliveryDocuments.get(0).setDeliveryDocumentLines(deliveryDocumentLines);
      oneDeliveryDocuments.add(deliveryDocuments.get(0));
      gdmDeliveryDocumentResponse.setDeliveryDocuments(oneDeliveryDocuments);
      doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
          .when(deliveryService)
          .findDeliveryDocument(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              instructionRequest.getUpcNumber(),
              httpHeaders);

      doReturn(null).when(instructionRepository).findByMessageId(instructionRequest.getMessageId());
      instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    } catch (ReceivingException receivingException) {
      assert (true);
    }
  }

  @Test
  public void testServeInstructionRequest_whenUpcInRequestAndHasMultipleDeliveryDocumentInGDM() {
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    ReflectionTestUtils.setField(
        instructionService, "instructionRepository", instructionRepository);
    try {
      when(configUtils.getFeatureFlagsByFacility("32987"))
          .thenReturn(defaultFeatureFlagsByFacility);
      GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
      List<DeliveryDocument> multipleDeliveryDocs = new ArrayList<>();
      multipleDeliveryDocs.add(MockInstruction.getDeliveryDocuments().get(0));
      multipleDeliveryDocs.add(MockInstruction.getDeliveryDocuments().get(0));
      gdmDeliveryDocumentResponse.setDeliveryDocuments(multipleDeliveryDocs);
      doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
          .when(deliveryService)
          .findDeliveryDocument(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              instructionRequest.getUpcNumber(),
              httpHeaders);
      doReturn(null).when(instructionRepository).findByMessageId(anyString());
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      verify(deliveryService, times(1))
          .findDeliveryDocument(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              instructionRequest.getUpcNumber(),
              httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() > 1);
      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getGtin(),
          instructionRequest.getUpcNumber());
      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getAdditionalInfo()
              .getWarehouseAreaCode(),
          "1");
      assert (true);
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test
  public void testCreateInstructionForUpcReceiving_hasOverage() {
    reset(receiptService);
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("");
    instructionRequest.setProblemTagId(null);
    instructionRequest.setOnline(Boolean.FALSE);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    doReturn(15l).when(receiptService).getReceivedQtyByPoAndPoLine("4763030227", 1);
    try {
      instructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
    } catch (ReceivingException receivingException) {
      assert (true);
      verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine("4763030227", 1);
    }
  }

  @Test
  public void testCreateInstructionForUpcReceiving_success() {
    reset(receiptService);
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("");
    instructionRequest.setProblemTagId(null);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    instructionRequest.setOnline(Boolean.FALSE);
    pendingInstruction.setInstructionCode("");
    when(deliveryItemOverrideService.findByDeliveryNumberAndItemNumber(
            Long.parseLong(instructionRequest.getDeliveryNumber()),
            instructionRequest
                .getDeliveryDocuments()
                .get(0)
                .getDeliveryDocumentLines()
                .get(0)
                .getItemNbr()))
        .thenReturn(Optional.empty());
    when(instructionRepository.getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine(
            anyString(), anyInt()))
        .thenReturn(0l);
    doReturn(1l).when(receiptService).getReceivedQtyByPoAndPoLine("4763030227", 1);
    when(instructionRepository.save(any())).thenReturn(pendingInstruction);
    doNothing()
        .when(jmsPublisher)
        .publish(Mockito.anyString(), Mockito.any(ReceivingJMSEvent.class), any(Boolean.class));
    try {
      doReturn(gson.toJson(fdeCreateContainerResponse)).when(fdeService).receive(any(), any());
      instructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      verify(receiptService, times(2)).getReceivedQtyByPoAndPoLine("4763030227", 1);
      assert (true);
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test
  public void testCreateInstructionForUpcReceiving_hasProblemTagId() throws ReceivingException {
    reset(receiptService);
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("");
    instructionRequest.setProblemTagId("1");
    instructionRequest.setOnline(Boolean.FALSE);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);

    when(deliveryItemOverrideService.findByDeliveryNumberAndItemNumber(
            Long.parseLong(instructionRequest.getDeliveryNumber()),
            instructionRequest
                .getDeliveryDocuments()
                .get(0)
                .getDeliveryDocumentLines()
                .get(0)
                .getItemNbr()))
        .thenReturn(Optional.empty());
    when(instructionRepository.getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine(
            anyString(), anyInt()))
        .thenReturn(0l);
    doReturn(1l).when(receiptService).getReceivedQtyByProblemId("1");
    when(instructionRepository.save(any())).thenReturn(pendingInstruction);
    doReturn(problemService)
        .when(configUtils)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.PROBLEM_SERVICE), eq(ProblemService.class));
    when(problemService.getProblemDetails(anyString()))
        .thenReturn(
            gson.fromJson(
                MockInstruction.getMockProblemLabel().getProblemResponse(),
                FitProblemTagResponse.class));
    try {
      doReturn(gson.toJson(fdeCreateContainerResponse)).when(fdeService).receive(any(), any());
      Instruction instruction =
          instructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      verify(receiptService, times(2)).getReceivedQtyByProblemId("1");
      assert (instruction == pendingInstruction);
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test
  public void testCreateInstructionForUpcReceiving_hasNoProblemTagId() {
    reset(receiptService);
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("");
    instructionRequest.setProblemTagId(null);
    instructionRequest.setOnline(Boolean.FALSE);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    when(deliveryItemOverrideService.findByDeliveryNumberAndItemNumber(
            Long.parseLong(instructionRequest.getDeliveryNumber()),
            instructionRequest
                .getDeliveryDocuments()
                .get(0)
                .getDeliveryDocumentLines()
                .get(0)
                .getItemNbr()))
        .thenReturn(Optional.empty());
    when(instructionRepository.getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine(
            anyString(), anyInt()))
        .thenReturn(0l);
    doReturn(1l).when(receiptService).getReceivedQtyByPoAndPoLine("4763030227", 1);
    when(instructionRepository.save(any())).thenReturn(pendingInstruction);
    try {
      doReturn(gson.toJson(fdeCreateContainerResponse)).when(fdeService).receive(any(), any());
      instructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      verify(receiptService, times(2)).getReceivedQtyByPoAndPoLine("4763030227", 1);
      assert (true);
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test
  public void testCreateInstructionForAsnReceiving_whenAlreadyReceived() {
    when(containerPersisterService.getContainerDetails("1232323133")).thenReturn(container);
    instructionRequest.setAsnBarcode("1232323133");
    try {
      instructionService.createInstructionForAsnReceiving(instructionRequest, httpHeaders);
    } catch (ReceivingException receivingException) {
      assertEquals(receivingException.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @Test
  public void testCreateInstructionForAsnReceiving_whenGdmDoesNotHasAsnInfo() {
    when(containerPersisterService.getContainerDetails("123456789")).thenReturn(null);
    instructionRequest.setAsnBarcode("123456789");
    try {
      when(deliveryService.getContainerInfoByAsnBarcode("123456789", httpHeaders))
          .thenThrow(new ReceivingException("", HttpStatus.INTERNAL_SERVER_ERROR));
      instructionService.createInstructionForAsnReceiving(instructionRequest, httpHeaders);
      assert (false);
    } catch (ReceivingException receivingException) {
      assertEquals(receivingException.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @Test
  public void testCreateInstructionForAsnReceiving_whenValidAsn() {
    reset(instructionRepository);
    reset(receiptService);
    reset(printJobService);
    reset(jmsPublisher);
    reset(JMSReceiptPublisher);

    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    when(containerPersisterService.getContainerDetails("00000077670099006775")).thenReturn(null);
    instructionRequest.setAsnBarcode("00000077670099006775");
    try {
      when(deliveryService.getContainerInfoByAsnBarcode("00000077670099006775", httpHeaders))
          .thenReturn(gdmContainerResponse);
      when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
          .thenReturn(gson.toJson(asnFdeCreateContainerResponse));
      doNothing()
          .when(dcFinService)
          .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
      Instruction asnInstruction =
          InstructionUtils.processInstructionResponseForS2S(
              instructionRequest, asnFdeCreateContainerResponse, 1, httpHeaders);
      when(instructionRepository.save(any())).thenReturn(asnInstruction);
      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);
      doNothing().when(JMSReceiptPublisher).publish(any(), any());
      when(configUtils.isFeatureFlagEnabled(ReceivingConstants.MOVE_DEST_BU_ENABLED))
          .thenReturn(false);

      instructionService.createInstructionForAsnReceiving(instructionRequest, httpHeaders);

      verify(instructionRepository, times(2)).save(any());
      verify(printJobService, times(1)).createPrintJob(anyLong(), any(), anySet(), anyString());
      verify(jmsPublisher, times(3)).publish(anyString(), any(), any(Boolean.class));
      verify(JMSReceiptPublisher, times(1)).publish(any(), any());
      verify(movePublisher, times(1))
          .publishMove(
              anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
      assert (true);
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test
  public void testCreateInstructionForAsnReceiving_whenValidAsnWithContainerItems() {
    reset(instructionRepository);
    reset(receiptService);
    reset(printJobService);
    reset(jmsPublisher);
    reset(JMSReceiptPublisher);
    when(containerPersisterService.getContainerDetails("00000077670099006775")).thenReturn(null);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    instructionRequest.setAsnBarcode("00000077670099006775");
    try {
      doNothing()
          .when(movePublisher)
          .publishMove(
              anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
      when(configUtils.isFeatureFlagEnabled(ReceivingConstants.MOVE_DEST_BU_ENABLED))
          .thenReturn(false);
      when(deliveryService.getContainerInfoByAsnBarcode("00000077670099006775", httpHeaders))
          .thenReturn(gdmContainerResponse1);
      when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
          .thenReturn(gson.toJson(asnFdeCreateContainerResponse));
      doNothing()
          .when(dcFinService)
          .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);
      Instruction asnInstruction =
          InstructionUtils.processInstructionResponseForS2S(
              instructionRequest, asnFdeCreateContainerResponse, 1, httpHeaders);
      when(instructionRepository.save(any())).thenReturn(asnInstruction);

      when(configUtils.getConfiguredInstance(
              "32987", ReceivingConstants.RECEIPT_EVENT_HANDLER, MessagePublisher.class))
          .thenReturn(JMSReceiptPublisher);
      doNothing().when(JMSReceiptPublisher).publish(any(), any());

      instructionService.createInstructionForAsnReceiving(instructionRequest, httpHeaders);

      verify(instructionRepository, times(2)).save(any());
      verify(printJobService, times(1)).createPrintJob(anyLong(), any(), anySet(), anyString());
      verify(jmsPublisher, times(3)).publish(anyString(), any(), any(Boolean.class));
      verify(JMSReceiptPublisher, times(1)).publish(any(), any());
      verify(movePublisher, times(1))
          .publishMove(
              anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
      assert (true);
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test
  public void testHasOpenInstruction() {

    when(instructionRepository.countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            anyLong()))
        .thenReturn(1l);
    assertEquals(instructionService.hasOpenInstruction(Long.valueOf("1")), true);
  }

  @Test
  public void testDoesNotHaveOpenInstruction() {

    when(instructionRepository.countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            anyLong()))
        .thenReturn(0l);
    assertEquals(instructionService.hasOpenInstruction(Long.valueOf("1")), false);
  }

  @Test
  public void testGetDeliveryDocsWithLithiumIonData_skipPublishDeliveryStatus()
      throws ReceivingException {
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    when(deliveryService.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenReturn(gson.toJson(gdmDeliveryDocumentRespose.getDeliveryDocuments()));
    InstructionResponse response =
        instructionService.serveInstructionRequest(
            gson.toJson(instructionReqWithWorkingState), httpHeaders);
    assertEquals(
        response.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0).getItemType(),
        null);
    assertEquals(
        response.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(1).getItemType(),
        LithiumIonType.METAL.getValue());
    assertEquals(
        response.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(2).getItemType(),
        LithiumIonType.ION.getValue());
  }

  @Test
  public void testGetDeliveryDocsWithLithiumIonData_publishDeliveryStatus()
      throws ReceivingException {
    when(deliveryService.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenReturn(gson.toJson(gdmDeliveryDocumentRespose.getDeliveryDocuments()));
    when(deliveryStatusPublisher.publishDeliveryStatus(
            Long.valueOf("98765819"),
            DeliveryStatus.WORKING.toString(),
            null,
            ReceivingUtils.getForwardablHeader(httpHeaders)))
        .thenReturn(null);

    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    when(configUtils.getProcessExpiry()).thenReturn(Boolean.FALSE);

    instructionReqWithWorkingState.setDeliveryStatus(DeliveryStatus.OPN.toString());
    InstructionResponse response =
        instructionService.serveInstructionRequest(
            gson.toJson(instructionReqWithWorkingState), httpHeaders);
    assertEquals(
        response.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0).getItemType(),
        null);
    assertEquals(
        response.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(1).getItemType(),
        LithiumIonType.METAL.getValue());
    assertEquals(
        response.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(2).getItemType(),
        LithiumIonType.ION.getValue());
  }

  @Test
  public void testGetInstructionWithMessageId_whenInstructionDoesNotExist() {
    try {
      doReturn(null).when(instructionRepository).findByMessageId(anyString());
      instructionService.getInstructionByMessageId("", httpHeaders);
    } catch (ReceivingException receivingException) {
      assertEquals(receivingException.getHttpStatus(), HttpStatus.NOT_FOUND);
    }
  }

  @Test
  public void testGetInstructionWithMessageId_whenInstructionExist() throws ReceivingException {
    doReturn(instructionByMessageId)
        .when(instructionRepository)
        .findByMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2b96f8");
    GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
    List<DeliveryDocument> multipleDeliveryDocs = new ArrayList<>();
    multipleDeliveryDocs.add(MockInstruction.getDeliveryDocuments().get(0));
    multipleDeliveryDocs.add(MockInstruction.getDeliveryDocuments().get(0));
    gdmDeliveryDocumentResponse.setDeliveryDocuments(multipleDeliveryDocs);
    when(deliveryService.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenReturn(gson.toJson(gdmDeliveryDocumentRespose.getDeliveryDocuments()));
    InstructionResponse instructionResponse =
        instructionService.getInstructionByMessageId(
            "58e1df00-ebf6-11e8-9c25-dd4bfc2b96f8", httpHeaders);
    assertEquals(instructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        instructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(), 1);
  }

  @Test
  public void testServeInstructionRequest_MultiLineWithWarehouseRotationType() {
    try {
      List<DeliveryDocument> deliveryDocs = gdmDeliveryDocumentRespose.getDeliveryDocuments();
      when(configUtils.getFeatureFlagsByFacility("32987"))
          .thenReturn(defaultFeatureFlagsByFacility);
      when(configUtils.getProcessExpiry()).thenReturn(Boolean.TRUE);
      when(deliveryService.findDeliveryDocument(anyLong(), anyString(), any()))
          .thenReturn(gson.toJson(deliveryDocs));
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(
              gson.toJson(instructionReqWithWorkingState), httpHeaders);

      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getFirstExpiryFirstOut(),
          Boolean.FALSE);
      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(1)
              .getFirstExpiryFirstOut(),
          Boolean.FALSE);
      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(2)
              .getFirstExpiryFirstOut(),
          Boolean.TRUE);

      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getWarehouseMinLifeRemainingToReceive(),
          deliveryDocs
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getWarehouseMinLifeRemainingToReceive());

      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(1)
              .getWarehouseMinLifeRemainingToReceive(),
          deliveryDocs
              .get(0)
              .getDeliveryDocumentLines()
              .get(1)
              .getWarehouseMinLifeRemainingToReceive());

      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(2)
              .getWarehouseMinLifeRemainingToReceive(),
          deliveryDocs
              .get(0)
              .getDeliveryDocumentLines()
              .get(2)
              .getWarehouseMinLifeRemainingToReceive());

    } catch (ReceivingException receivingException) {
      receivingException.printStackTrace();
      assert (false);
    }
  }

  @Test
  public void testServeInstructionRequest_validateItemMinLifeRemaining() throws ReceivingException {
    try {
      List<DeliveryDocument> deliveryDocs =
          MockGdmResponse.getGdmResponseWithInvalidItemInfo().getDeliveryDocuments();

      when(configUtils.getFeatureFlagsByFacility("32987"))
          .thenReturn(defaultFeatureFlagsByFacility);
      when(deliveryService.findDeliveryDocument(anyLong(), anyString(), any()))
          .thenReturn(gson.toJson(deliveryDocs));

      instructionService.serveInstructionRequest(
          gson.toJson(instructionReqWithWorkingState), httpHeaders);
    } catch (ReceivingException receivingException) {
      assertEquals(receivingException.getHttpStatus(), HttpStatus.BAD_REQUEST);
      assertEquals(
          receivingException.getMessage(),
          String.format(ReceivingException.INVALID_ITEM_ERROR_MSG, "436617391"));
    }
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testCreateInstructionForUpcReceiving_LimitNewInstruction() throws ReceivingException {

    Instruction instruction2 = new Instruction();
    instruction2.setId(Long.valueOf("2"));
    instruction2.setContainer(MockInstruction.getContainerDetails());
    instruction2.setChildContainers(null);
    instruction2.setCreateTs(new Date());
    instruction2.setCreateUserId("sysadmin");
    instruction2.setLastChangeTs(new Date());
    instruction2.setLastChangeUserId("sysadmin");
    instruction2.setDeliveryNumber(Long.valueOf("21119003"));
    instruction2.setGtin("00000943037204");
    instruction2.setInstructionCode("Build Container");
    instruction2.setInstructionMsg("Build the Container");
    instruction2.setItemDescription("HEM VALUE PACK (5)");
    instruction2.setActivityName("DA");
    instruction2.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2b96f8");
    instruction2.setMove(MockInstruction.getMoveData());
    instruction2.setPoDcNumber("32899");
    instruction2.setPrintChildContainerLabels(true);
    instruction2.setPurchaseReferenceNumber("9763140005");
    instruction2.setPurchaseReferenceLineNumber(1);
    instruction2.setProjectedReceiveQty(5);
    instruction2.setProviderId("DA");
    instruction2.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction2.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    List<Instruction> instructionList = new ArrayList<>();
    instructionList.add(instruction2);

    reset(receiptService);
    instructionRequest.setDeliveryNumber("21119003");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setProblemTagId(null);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    doReturn(10l).when(receiptService).getReceivedQtyByPoAndPoLine("4763030227", 1);
    when(instructionRepository.getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine(
            "4763030227", 1))
        .thenReturn(5l);
    try {
      instructionError = InstructionErrorCode.getErrorValue(ReceivingException.MULTI_USER_ERROR);
      instructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
    } catch (ReceivingException receivingException) {
      assert (true);
      verify(instructionRepository, times(1))
          .getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine("4763030227", 1);
      assertEquals(
          receivingException.getErrorResponse().getErrorMessage(),
          String.format(instructionError.getErrorMessage(), "sysadmin"));
      throw receivingException;
    }
  }

  @Test
  public void testGetDeliveryDocsAndCreateInstructionForPoCon() throws ReceivingException {

    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    when(deliveryService.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenReturn(gson.toJson(gdmDeliveryDocumentResponseForPoCon.getDeliveryDocuments()));
    when(deliveryService.getPOInfoFromDelivery(anyLong(), any(), any()))
        .thenReturn(gson.toJson(gdmDeliveryDocumentResponseForPoCon));
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    doReturn(pendingInstruction).when(instructionRepository).save(any());
    doReturn(gson.toJson(fdeCreateContainerResponse)).when(fdeService).receive(any(), any());
    when(receiptService.getReceivedQtyByPoAndDeliveryNumber(any(), any())).thenReturn(0L);
    InstructionResponse response =
        instructionService.serveInstructionRequest(
            gson.toJson(poConInstructionRequest), httpHeaders);
    assertEquals(response.getDeliveryDocuments().size(), 1);
    assertEquals(response.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(), 3);
  }

  @Test
  public void testGetDeliveryDocsAndCreateInstructioninMultiPoForPoCon() throws ReceivingException {

    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    when(deliveryService.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenReturn(gson.toJson(gdmDeliveryDocumentResponseForPoCon.getDeliveryDocuments()));
    when(deliveryService.getPOInfoFromDelivery(anyLong(), any(), any()))
        .thenReturn(gson.toJson(gdmDeliveryDocumentResponseForPoCon));
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    doReturn(pendingInstruction).when(instructionRepository).save(any());
    doReturn(gson.toJson(fdeCreateContainerResponse)).when(fdeService).receive(any(), any());
    when(receiptService.getReceivedQtyByPoAndDeliveryNumber(any(), any())).thenReturn(0L);

    InstructionRequest instructionRequest = poConInstructionRequestWithDeliveryDocuments;
    instructionRequest.setIsPOCON(null);
    InstructionResponse response =
        instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertEquals(response.getDeliveryDocuments().size(), 1);
    assertEquals(response.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(), 3);
  }

  @Test
  public void testGetDeliveryDocsForPoConWhenCubeUomNotPresent() throws ReceivingException {
    gdmError = GdmErrorCode.getErrorValue(ReceivingException.MISSING_ITEM_INFO_ERROR);
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    gdmDeliveryDocumentResponseForPoCon.getDeliveryDocuments().get(0).setCubeUOM(null);
    when(deliveryService.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenReturn(gson.toJson(gdmDeliveryDocumentResponseForPoCon.getDeliveryDocuments()));
    when(deliveryService.getPOInfoFromDelivery(anyLong(), any(), any()))
        .thenReturn(gson.toJson(gdmDeliveryDocumentResponseForPoCon));
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    doReturn(pendingInstruction).when(instructionRepository).save(any());
    doReturn(gson.toJson(fdeCreateContainerResponse)).when(fdeService).receive(any(), any());
    when(receiptService.getReceivedQtyByPoAndDeliveryNumber(any(), any())).thenReturn(0L);
    try {
      instructionService.serveInstructionRequest(
          gson.toJson(poConInstructionRequestWithDeliveryDocuments), httpHeaders);
    } catch (ReceivingException receivingException) {
      assert (true);
      assertEquals(
          receivingException.getErrorResponse().getErrorMessage(), gdmError.getErrorMessage());
    }
  }

  @Test
  public void poConFeatureFlagDisabledTest() throws ReceivingException {
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.POCON_FEATURE_FLAG, 32987))
        .thenReturn(false);
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    when(deliveryService.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenReturn(gson.toJson(gdmDeliveryDocumentResponseForPoCon.getDeliveryDocuments()));
    when(deliveryService.getPOInfoFromDelivery(anyLong(), any(), any()))
        .thenReturn(gson.toJson(gdmDeliveryDocumentResponseForPoCon));
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    doReturn(pendingInstruction).when(instructionRepository).save(any());
    doReturn(gson.toJson(fdeCreateContainerResponse)).when(fdeService).receive(any(), any());
    when(receiptService.getReceivedQtyByPoAndDeliveryNumber(any(), any())).thenReturn(0L);
    InstructionResponse response =
        instructionService.serveInstructionRequest(
            gson.toJson(poConInstructionRequest), httpHeaders);
    assertEquals(response.getDeliveryDocuments().size(), 1);
    assertEquals(response.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(), 3);
  }

  @Test
  public void testGetDeliveryDocsForPoConWhenWeightUomNotPresent() throws ReceivingException {
    gdmError = GdmErrorCode.getErrorValue(ReceivingException.MISSING_ITEM_INFO_ERROR);
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    gdmDeliveryDocumentResponseForPoCon.getDeliveryDocuments().get(0).setWeightUOM(null);
    when(deliveryService.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenReturn(gson.toJson(gdmDeliveryDocumentResponseForPoCon.getDeliveryDocuments()));
    when(deliveryService.getPOInfoFromDelivery(anyLong(), any(), any()))
        .thenReturn(gson.toJson(gdmDeliveryDocumentResponseForPoCon));
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    doReturn(pendingInstruction).when(instructionRepository).save(any());
    doReturn(gson.toJson(fdeCreateContainerResponse)).when(fdeService).receive(any(), any());
    when(receiptService.getReceivedQtyByPoAndDeliveryNumber(any(), any())).thenReturn(0L);
    try {
      instructionService.serveInstructionRequest(
          gson.toJson(poConInstructionRequestWithDeliveryDocuments), httpHeaders);
    } catch (ReceivingException receivingException) {
      assert (true);
      assertEquals(
          receivingException.getErrorResponse().getErrorMessage(), gdmError.getErrorMessage());
    }
  }

  @Test
  public void testServeInstructionRequestForManual_itemInSinglePoPol() throws ReceivingException {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDeliveryNumber("21119003");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    instructionRequest.setManualReceivingEnabled(Boolean.TRUE);
    List<DeliveryDocument> deliveryDocuments1 = MockInstruction.getDeliveryDocuments();
    GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
    List<DeliveryDocument> oneDeliveryDocuments = new ArrayList<>();
    oneDeliveryDocuments.add(deliveryDocuments1.get(0));
    gdmDeliveryDocumentResponse.setDeliveryDocuments(oneDeliveryDocuments);
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse1 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("4763030227", 1, 0L);
    List<ReceiptSummaryQtyByPoAndPoLineResponse> receiptSummaryQtyByPoAndPoLineResponses =
        new ArrayList<>();
    receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse1);
    doReturn(receiptSummaryQtyByPoAndPoLineResponses)
        .when(receiptService)
        .receivedQtyInVNPKByPoAndPoLineList(anyList(), anySet());
    doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
        .when(deliveryService)
        .findDeliveryDocument(
            Long.parseLong(instructionRequest.getDeliveryNumber()),
            instructionRequest.getUpcNumber(),
            httpHeaders);
    InstructionResponse instructionResponse =
        instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getDeliveryDocuments());
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
  }

  @Test
  public void testServeInstructionRequestForOverflowConveyableItem_InSinglePoPol()
      throws ReceivingException {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDeliveryNumber("21119003");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    instructionRequest.setManualReceivingEnabled(Boolean.FALSE);
    instructionRequest.setOverflowReceiving(Boolean.TRUE);
    List<DeliveryDocument> deliveryDocuments1 = MockInstruction.getDeliveryDocuments();
    GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
    List<DeliveryDocument> oneDeliveryDocuments = new ArrayList<>();
    oneDeliveryDocuments.add(deliveryDocuments1.get(0));
    gdmDeliveryDocumentResponse.setDeliveryDocuments(oneDeliveryDocuments);
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse1 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("4763030227", 1, 0L);
    List<ReceiptSummaryQtyByPoAndPoLineResponse> receiptSummaryQtyByPoAndPoLineResponses =
        new ArrayList<>();
    receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse1);
    doReturn(receiptSummaryQtyByPoAndPoLineResponses)
        .when(receiptService)
        .receivedQtyInVNPKByPoAndPoLineList(anyList(), anySet());
    doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
        .when(deliveryService)
        .findDeliveryDocument(
            Long.parseLong(instructionRequest.getDeliveryNumber()),
            instructionRequest.getUpcNumber(),
            httpHeaders);
    InstructionResponse instructionResponse =
        instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getDeliveryDocuments());
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
  }

  @Test
  public void testServeInstructionRequestForManual_itemInSinglePoPolPOCON()
      throws ReceivingException {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDeliveryNumber("21119003");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    instructionRequest.setManualReceivingEnabled(Boolean.TRUE);

    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.POCON_FEATURE_FLAG, 32987))
        .thenReturn(true);
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse1 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("0294235326", 1, 0L);
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse2 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("0294235326", 2, 0L);
    List<ReceiptSummaryQtyByPoAndPoLineResponse> receiptSummaryQtyByPoAndPoLineResponses =
        new ArrayList<>();
    receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse1);
    receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse2);
    doReturn(receiptSummaryQtyByPoAndPoLineResponses)
        .when(receiptService)
        .receivedQtyInVNPKByPoAndPoLineList(anyList(), anySet());

    doReturn(gson.toJson(gdmDeliveryDocumentResponseForPoCon.getDeliveryDocuments()))
        .when(deliveryService)
        .findDeliveryDocument(
            Long.parseLong(instructionRequest.getDeliveryNumber()),
            instructionRequest.getUpcNumber(),
            httpHeaders);
    InstructionResponse instructionResponse =
        instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getDeliveryDocuments());
    assertTrue(instructionResponse.getDeliveryDocuments().size() == 1);
  }

  @Test
  public void testServeInstructionRequestForManual_itemInMultiPoPol() throws ReceivingException {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDeliveryNumber("21119003");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    instructionRequest.setManualReceivingEnabled(Boolean.TRUE);
    List<DeliveryDocument> deliveryDocuments1 = MockInstruction.getDeliveryDocuments();
    GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    deliveryDocuments.add(deliveryDocuments1.get(0));
    deliveryDocuments.add(deliveryDocuments1.get(0));
    gdmDeliveryDocumentResponse.setDeliveryDocuments(deliveryDocuments);
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse1 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("4763030227", 1, 0L);
    List<ReceiptSummaryQtyByPoAndPoLineResponse> receiptSummaryQtyByPoAndPoLineResponses =
        new ArrayList<>();
    receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse1);
    doReturn(receiptSummaryQtyByPoAndPoLineResponses)
        .when(receiptService)
        .receivedQtyInVNPKByPoAndPoLineList(anyList(), anySet());
    doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
        .when(deliveryService)
        .findDeliveryDocument(
            Long.parseLong(instructionRequest.getDeliveryNumber()),
            instructionRequest.getUpcNumber(),
            httpHeaders);
    InstructionResponse instructionResponse =
        instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getDeliveryDocuments());
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 1);
  }

  @Test
  public void testServeInstructionRequestForOverflowConveyableItem_InMultiPoPol()
      throws ReceivingException {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDeliveryNumber("21119003");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    instructionRequest.setManualReceivingEnabled(Boolean.FALSE);
    instructionRequest.setOverflowReceiving(Boolean.TRUE);
    List<DeliveryDocument> deliveryDocuments1 = MockInstruction.getDeliveryDocuments();
    GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    deliveryDocuments.add(deliveryDocuments1.get(0));
    deliveryDocuments.add(deliveryDocuments1.get(0));
    gdmDeliveryDocumentResponse.setDeliveryDocuments(deliveryDocuments);
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse1 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("4763030227", 1, 0L);
    List<ReceiptSummaryQtyByPoAndPoLineResponse> receiptSummaryQtyByPoAndPoLineResponses =
        new ArrayList<>();
    receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse1);
    doReturn(receiptSummaryQtyByPoAndPoLineResponses)
        .when(receiptService)
        .receivedQtyInVNPKByPoAndPoLineList(anyList(), anySet());
    doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
        .when(deliveryService)
        .findDeliveryDocument(
            Long.parseLong(instructionRequest.getDeliveryNumber()),
            instructionRequest.getUpcNumber(),
            httpHeaders);
    InstructionResponse instructionResponse =
        instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getDeliveryDocuments());
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 1);
  }

  @Test
  public void testServeInstructionRequestForManual_requestHasDeliveryDoc()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDeliveryNumber("21119003");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    instructionRequest.setManualReceivingEnabled(Boolean.TRUE);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.RELOAD_DELIVERY_DOCUMENT_FEATURE_FLAG))
        .thenReturn(true);
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../receiving-test/src/main/resources/json/manual_instruction_response.json")
                            .getCanonicalPath()))));
    doReturn(0L).when(receiptService).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    when(instructionPersisterService.saveInstruction(any())).thenAnswer(returnsFirstArg());
    when(receiptRepository.saveAll(anyList())).thenAnswer(returnsFirstArg());
    when(containerPersisterService.saveContainer(any())).thenAnswer(returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(returnsFirstArg());
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    when(configUtils.getConfiguredInstance(
            "32987", ReceivingConstants.RECEIPT_EVENT_HANDLER, MessagePublisher.class))
        .thenReturn(JMSReceiptPublisher);
    when(configUtils.getCcmValue(
            getFacilityNum(), ELIGIBLE_TRANSFER_POS_CCM_CONFIG, DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE))
        .thenReturn(DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE);
    doNothing().when(JMSReceiptPublisher).publish(any(), any());
    InstructionResponse instructionResponse =
        instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(instructionResponse.getInstruction());
  }

  @Test
  public void testServeInstructionRequestForManual_requestHasDeliveryDoc_FetchDeliveryDoc()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDeliveryNumber("21119003");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    instructionRequest.setManualReceivingEnabled(Boolean.TRUE);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.RELOAD_DELIVERY_DOCUMENT_FEATURE_FLAG))
        .thenReturn(true);
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../receiving-test/src/main/resources/json/manual_instruction_response.json")
                            .getCanonicalPath()))));
    doReturn(0L).when(receiptService).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    when(instructionPersisterService.saveInstruction(any())).thenAnswer(returnsFirstArg());
    when(receiptRepository.saveAll(anyList())).thenAnswer(returnsFirstArg());
    when(containerPersisterService.saveContainer(any())).thenAnswer(returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(returnsFirstArg());
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    when(configUtils.getConfiguredInstance(
            "32987", ReceivingConstants.RECEIPT_EVENT_HANDLER, MessagePublisher.class))
        .thenReturn(JMSReceiptPublisher);
    when(configUtils.getCcmValue(
            getFacilityNum(), ELIGIBLE_TRANSFER_POS_CCM_CONFIG, DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE))
        .thenReturn(DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE);
    doNothing().when(JMSReceiptPublisher).publish(any(), any());
    InstructionResponse instructionResponse =
        instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(instructionResponse.getInstruction());
  }

  @Test
  public void testGetDeliveryDocsAndCreateInstructioninMultiPoForPoConHavingUnknowChannelMethod()
      throws ReceivingException {
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    when(deliveryService.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenReturn(
            gson.toJson(
                gdmDeliveryDocumentResponseForPoConWithUnknownChannelMethod
                    .getDeliveryDocuments()));
    when(deliveryService.getPOInfoFromDelivery(anyLong(), any(), any()))
        .thenReturn(gson.toJson(gdmDeliveryDocumentResponseForPoConWithUnknownChannelMethod));
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    doReturn(pendingInstruction).when(instructionRepository).save(any());
    doReturn(gson.toJson(fdeCreateContainerResponse)).when(fdeService).receive(any(), any());
    when(receiptService.getReceivedQtyByPoAndDeliveryNumber(any(), any())).thenReturn(0L);
    InstructionResponse response =
        instructionService.serveInstructionRequest(
            gson.toJson(poConInstructionRequestWithDeliveryDocuments), httpHeaders);
    assertEquals(response.getDeliveryDocuments().size(), 1);
    assertEquals(response.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(), 1);
  }

  @Test
  public void testServeInstructionRequest_SingleItemMultiPoLine()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDeliveryNumber("21119003");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setMessageId("messageId");
    List<DeliveryDocument> deliveryDocumentsList = new ArrayList<>();
    List<DeliveryDocument> deliveryDocuments1 = MockInstruction.getDeliveryDocuments();
    deliveryDocumentsList.add(deliveryDocuments1.get(0));
    deliveryDocumentsList.add(deliveryDocuments1.get(0));
    GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
    gdmDeliveryDocumentResponse.setDeliveryDocuments(deliveryDocumentsList);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    ReflectionTestUtils.setField(instructionService, "configUtils", configUtils);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.ALLOW_SINGLE_ITEM_MULTI_PO_LINE);
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../receiving-test/src/main/resources/json/manual_instruction_response.json")
                            .getCanonicalPath()))));
    doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
        .when(deliveryService)
        .findDeliveryDocument(
            Long.parseLong(instructionRequest.getDeliveryNumber()),
            instructionRequest.getUpcNumber(),
            httpHeaders);
    doReturn(MockInstruction.getInstruction())
        .when(instructionRepository)
        .findByMessageId(anyString());
    InstructionResponse instructionResponse =
        instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(instructionResponse.getInstruction());
  }

  @Test
  public void testServeInstructionRequest_SingleItemSinglePoMultiPoLine()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDeliveryNumber("21119003");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setMessageId("messageId");
    List<DeliveryDocument> deliveryDocumentsList = new ArrayList<>();
    List<DeliveryDocument> deliveryDocuments1 = MockInstruction.getDeliveryDocuments();
    deliveryDocuments1
        .get(0)
        .getDeliveryDocumentLines()
        .add(deliveryDocuments1.get(0).getDeliveryDocumentLines().get(0));
    deliveryDocumentsList.add(deliveryDocuments1.get(0));
    GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
    gdmDeliveryDocumentResponse.setDeliveryDocuments(deliveryDocumentsList);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    ReflectionTestUtils.setField(instructionService, "configUtils", configUtils);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.ALLOW_SINGLE_ITEM_MULTI_PO_LINE);
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../receiving-test/src/main/resources/json/manual_instruction_response.json")
                            .getCanonicalPath()))));
    doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
        .when(deliveryService)
        .findDeliveryDocument(
            Long.parseLong(instructionRequest.getDeliveryNumber()),
            instructionRequest.getUpcNumber(),
            httpHeaders);
    doReturn(MockInstruction.getInstruction())
        .when(instructionRepository)
        .findByMessageId(anyString());
    InstructionResponse instructionResponse =
        instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(instructionResponse.getInstruction());
  }

  @Test
  public void testServeInstructionRequest_withLithiumIonItemWithGroundTransportationMode()
      throws ReceivingException {
    List<DeliveryDocument> deliveryDocuments1 = MockInstruction.getDeliveryDocuments();

    TransportationModes transportationModes = new TransportationModes();
    Mode mode = new Mode();
    mode.setCode(1);
    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("N/A");
    transportationModes.setDotHazardousClass(dotHazardousClass);
    transportationModes.setProperShipping("Lithium Ion Battery Packed with Equipment");
    transportationModes.setPkgInstruction(Arrays.asList("965", "966"));
    transportationModes.setMode(mode);

    deliveryDocuments1
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes(Collections.singletonList(transportationModes));
    reset(deliveryService);
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    instructionRequest.setManualReceivingEnabled(Boolean.FALSE);
    ReflectionTestUtils.setField(instructionServiceSpied, "gson", gson);
    when(instructionRepository.findByMessageId(any())).thenReturn(null);
    doReturn(defaultFeatureFlagsByFacility).when(configUtils).getFeatureFlagsByFacility("32835");
    GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
    List<DeliveryDocument> oneDeliveryDocuments = new ArrayList<>();
    oneDeliveryDocuments.add(deliveryDocuments1.get(0));
    gdmDeliveryDocumentResponse.setDeliveryDocuments(oneDeliveryDocuments);

    try {
      doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
          .when(deliveryService)
          .findDeliveryDocument(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              instructionRequest.getUpcNumber(),
              httpHeaders);
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertNull(instructionResponse.getInstruction());
    } catch (ReceivingException receivingException) {
      assert (false);
    }

    deliveryDocuments1
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setLithiumIonVerifiedOn(Date.from(Instant.parse("2018-10-19T06:00:00.000Z")));
    try {
      doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
          .when(deliveryService)
          .findDeliveryDocument(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              instructionRequest.getUpcNumber(),
              httpHeaders);
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertNull(instructionResponse.getInstruction());
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test
  public void testServeInstructionRequest_withLithiumIonItemWithMultipleTransportationModes()
      throws ReceivingException {
    List<DeliveryDocument> deliveryDocuments1 = MockInstruction.getDeliveryDocuments();

    TransportationModes transportationModes1 = new TransportationModes();
    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("N/A");
    transportationModes1.setDotHazardousClass(dotHazardousClass);
    Mode mode1 = new Mode();
    mode1.setCode(1);
    transportationModes1.setProperShipping("Lithium Metal BATTERY Contained in Equipment");
    transportationModes1.setPkgInstruction(Arrays.asList("969"));
    transportationModes1.setMode(mode1);

    TransportationModes transportationModes2 = new TransportationModes();
    Mode mode2 = new Mode();
    mode2.setCode(2);
    DotHazardousClass dotHazardousClass2 = new DotHazardousClass();
    dotHazardousClass2.setCode("N/A");
    transportationModes2.setDotHazardousClass(dotHazardousClass2);
    transportationModes2.setProperShipping("Lithium Metal Battery");
    transportationModes2.setPkgInstruction(Arrays.asList("967"));
    transportationModes2.setMode(mode2);

    List<TransportationModes> transportationModes = new ArrayList<>();
    transportationModes.add(transportationModes1);
    transportationModes.add(transportationModes2);

    deliveryDocuments1
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes((transportationModes));
    reset(deliveryService);
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    instructionRequest.setManualReceivingEnabled(Boolean.FALSE);
    ReflectionTestUtils.setField(instructionServiceSpied, "gson", gson);
    when(instructionRepository.findByMessageId(any())).thenReturn(null);
    doReturn(defaultFeatureFlagsByFacility).when(configUtils).getFeatureFlagsByFacility("32835");
    GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
    List<DeliveryDocument> oneDeliveryDocuments = new ArrayList<>();
    oneDeliveryDocuments.add(deliveryDocuments1.get(0));
    gdmDeliveryDocumentResponse.setDeliveryDocuments(oneDeliveryDocuments);
    doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
        .when(deliveryService)
        .findDeliveryDocument(
            Long.parseLong(instructionRequest.getDeliveryNumber()),
            instructionRequest.getUpcNumber(),
            httpHeaders);

    try {
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertNull(instructionResponse.getInstruction());
    } catch (ReceivingException receivingException) {
      assert (false);
    }

    deliveryDocuments1
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setLithiumIonVerifiedOn(Date.from(Instant.parse("2018-10-19T06:00:00.000Z")));
    try {
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertNull(instructionResponse.getInstruction());
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test
  public void testServeInstructionRequest_withLimitedQty() throws ReceivingException {

    List<DeliveryDocument> deliveryDocuments1 = MockInstruction.getDeliveryDocuments();

    TransportationModes transportationModes = new TransportationModes();
    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("ORM-D");
    Mode mode = new Mode();
    mode.setCode(1);
    transportationModes.setDotHazardousClass(dotHazardousClass);
    transportationModes.setMode(mode);
    transportationModes.setDotRegionCode(null);
    transportationModes.setDotIdNbr(null);
    transportationModes.setLimitedQty(null);

    deliveryDocuments1
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes(Collections.singletonList(transportationModes));
    reset(deliveryService);
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());

    when(instructionRepository.findByMessageId(any())).thenReturn(null);
    doReturn(defaultFeatureFlagsByFacility).when(configUtils).getFeatureFlagsByFacility("32835");
    GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
    List<DeliveryDocument> oneDeliveryDocuments = new ArrayList<>();
    oneDeliveryDocuments.add(deliveryDocuments1.get(0));
    gdmDeliveryDocumentResponse.setDeliveryDocuments(oneDeliveryDocuments);

    try {
      doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
          .when(deliveryService)
          .findDeliveryDocument(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              instructionRequest.getUpcNumber(),
              httpHeaders);
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertNull(instructionResponse.getInstruction());
    } catch (ReceivingException receivingException) {
      assert (false);
    }

    deliveryDocuments1
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setLimitedQtyVerifiedOn(Date.from(Instant.parse("2018-10-19T06:00:00.000Z")));
    try {
      doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
          .when(deliveryService)
          .findDeliveryDocument(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              instructionRequest.getUpcNumber(),
              httpHeaders);
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertNull(instructionResponse.getInstruction());
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test
  public void testServeInstructionRequest_withBothLithiumIonAndLimitedQtyItem()
      throws ReceivingException {
    List<DeliveryDocument> deliveryDocuments1 = MockInstruction.getDeliveryDocuments();
    TransportationModes transportationModes = new TransportationModes();
    Mode mode = new Mode();
    mode.setCode(1);
    transportationModes.setProperShipping("LITHIUM ION BATTERY PACKED WITH Equipment");
    transportationModes.setPkgInstruction(Arrays.asList("965", "966"));
    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("ORM-D");
    transportationModes.setDotRegionCode(null);
    transportationModes.setDotIdNbr(null);
    transportationModes.setLimitedQty(null);
    transportationModes.setPkgInstruction(Arrays.asList("967"));

    transportationModes.setDotHazardousClass(dotHazardousClass);
    transportationModes.setMode(mode);

    deliveryDocuments1
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes(Collections.singletonList(transportationModes));

    reset(deliveryService);
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    ReflectionTestUtils.setField(instructionServiceSpied, "gson", gson);
    when(instructionRepository.findByMessageId(any())).thenReturn(null);
    doReturn(defaultFeatureFlagsByFacility).when(configUtils).getFeatureFlagsByFacility("32835");
    GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
    List<DeliveryDocument> oneDeliveryDocuments = new ArrayList<>();
    oneDeliveryDocuments.add(deliveryDocuments1.get(0));
    gdmDeliveryDocumentResponse.setDeliveryDocuments(oneDeliveryDocuments);
    doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
        .when(deliveryService)
        .findDeliveryDocument(
            Long.parseLong(instructionRequest.getDeliveryNumber()),
            instructionRequest.getUpcNumber(),
            httpHeaders);

    try {
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertNull(instructionResponse.getInstruction());
    } catch (ReceivingException receivingException) {
      assert (false);
    }

    deliveryDocuments1
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setLimitedQtyVerifiedOn(Date.from(Instant.parse("2018-10-19T06:00:00.000Z")));
    try {
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertNull(instructionResponse.getInstruction());
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test
  public void testServeInstructionRequest_VerifyLithiumIonItem() throws ReceivingException {
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    instructionRequest.setRegulatedItemType(VendorCompliance.LITHIUM_ION);

    doReturn(MockInstruction.getInstruction())
        .when(instructionRepository)
        .findByMessageId(anyString());
    when(configUtils.getFeatureFlagsByFacility(anyString()))
        .thenReturn(defaultFeatureFlagsByFacility);
    doNothing().when(deliveryService).setVendorComplianceDateOnGDM(any(), any());

    // lithium-ion
    try {
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertNotNull(instructionResponse.getInstruction());
    } catch (ReceivingException receivingException) {
      assert (false);
    }

    // lithium/limitedqty
    instructionRequest.setRegulatedItemType(VendorCompliance.LITHIUM_ION_AND_LIMITED_QUANTITY);
    try {
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertNotNull(instructionResponse.getInstruction());
    } catch (ReceivingException receivingException) {
      assert (false);
    }

    // limited-qty
    instructionRequest.setRegulatedItemType(VendorCompliance.LIMITED_QTY);
    try {
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertNotNull(instructionResponse.getInstruction());
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test
  public void testServeInstructionRequest_withInvalidTransportationCode()
      throws ReceivingException {

    List<DeliveryDocument> deliveryDocuments1 = MockInstruction.getDeliveryDocuments();

    TransportationModes transportationModes = new TransportationModes();
    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("ORM-D");
    Mode mode = new Mode();
    mode.setCode(2);
    transportationModes.setDotHazardousClass(dotHazardousClass);
    transportationModes.setMode(mode);
    transportationModes.setDotRegionCode(null);
    transportationModes.setDotIdNbr(null);
    transportationModes.setLimitedQty(null);

    deliveryDocuments1
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes(Collections.singletonList(transportationModes));
    reset(deliveryService);
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());

    when(instructionRepository.findByMessageId(any())).thenReturn(null);
    doReturn(defaultFeatureFlagsByFacility).when(configUtils).getFeatureFlagsByFacility("32835");
    when(instructionRepository.findByMessageId(anyString()))
        .thenReturn(MockInstruction.getInstruction());
    GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
    List<DeliveryDocument> oneDeliveryDocuments = new ArrayList<>();
    oneDeliveryDocuments.add(deliveryDocuments1.get(0));
    gdmDeliveryDocumentResponse.setDeliveryDocuments(oneDeliveryDocuments);

    try {
      doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
          .when(deliveryService)
          .findDeliveryDocument(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              instructionRequest.getUpcNumber(),
              httpHeaders);
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertNotNull(instructionResponse.getInstruction());
    } catch (ReceivingException receivingException) {
      assert (false);
    }

    deliveryDocuments1
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setLimitedQtyVerifiedOn(Date.from(Instant.parse("2018-10-19T06:00:00.000Z")));
    try {
      doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
          .when(deliveryService)
          .findDeliveryDocument(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              instructionRequest.getUpcNumber(),
              httpHeaders);
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertNotNull(instructionResponse.getInstruction());
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test
  public void testServeInstructionRequest_withLimitedQtyVendorComplaince()
      throws ReceivingException {

    List<DeliveryDocument> deliveryDocuments1 = MockInstruction.getDeliveryDocuments();

    TransportationModes transportationModes = new TransportationModes();
    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("ORM-D");
    Mode mode = new Mode();
    mode.setCode(1);
    transportationModes.setDotHazardousClass(dotHazardousClass);
    transportationModes.setMode(mode);
    transportationModes.setDotRegionCode(null);
    transportationModes.setDotIdNbr(null);
    transportationModes.setLimitedQty(null);

    deliveryDocuments1
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes(Collections.singletonList(transportationModes));
    reset(deliveryService);
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    when(instructionRepository.findByMessageId(anyString()))
        .thenReturn(MockInstruction.getInstruction());
    doReturn(defaultFeatureFlagsByFacility).when(configUtils).getFeatureFlagsByFacility("32835");
    GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
    List<DeliveryDocument> oneDeliveryDocuments = new ArrayList<>();
    oneDeliveryDocuments.add(deliveryDocuments1.get(0));
    gdmDeliveryDocumentResponse.setDeliveryDocuments(oneDeliveryDocuments);

    try {
      doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
          .when(deliveryService)
          .findDeliveryDocument(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              instructionRequest.getUpcNumber(),
              httpHeaders);
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertNull(instructionResponse.getInstruction());
    } catch (ReceivingException receivingException) {
      assert (false);
    }

    deliveryDocuments1.get(0).getDeliveryDocumentLines().get(0).setLimitedQtyVerifiedOn(new Date());
    try {
      doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
          .when(deliveryService)
          .findDeliveryDocument(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              instructionRequest.getUpcNumber(),
              httpHeaders);
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertNotNull(instructionResponse.getInstruction());
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test
  public void testServeInstructionRequest_regulatedItemIsNull() throws ReceivingException {
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    instructionRequest.setRegulatedItemType(null);

    doReturn(MockInstruction.getInstruction())
        .when(instructionRepository)
        .findByMessageId(anyString());
    when(configUtils.getFeatureFlagsByFacility(anyString()))
        .thenReturn(defaultFeatureFlagsByFacility);
    doNothing().when(deliveryService).setVendorComplianceDateOnGDM(any(), any());

    try {
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertNotNull(instructionResponse.getInstruction());
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test
  public void testServeInstructionRequest_withLimitedQtyVendorComplainceAndManualReceivingIsTrue()
      throws ReceivingException {

    List<DeliveryDocument> deliveryDocuments1 = MockInstruction.getDeliveryDocuments();

    TransportationModes transportationModes = new TransportationModes();
    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("ORM-D");
    Mode mode = new Mode();
    mode.setCode(1);
    transportationModes.setDotHazardousClass(dotHazardousClass);
    transportationModes.setMode(mode);
    transportationModes.setDotRegionCode(null);
    transportationModes.setDotIdNbr(null);
    transportationModes.setLimitedQty(null);

    deliveryDocuments1
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes(Collections.singletonList(transportationModes));
    reset(deliveryService);
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setManualReceivingEnabled(true);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    when(instructionRepository.findByMessageId(anyString()))
        .thenReturn(MockInstruction.getInstruction());
    doReturn(defaultFeatureFlagsByFacility).when(configUtils).getFeatureFlagsByFacility("32835");
    GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
    List<DeliveryDocument> oneDeliveryDocuments = new ArrayList<>();
    oneDeliveryDocuments.add(deliveryDocuments1.get(0));
    gdmDeliveryDocumentResponse.setDeliveryDocuments(oneDeliveryDocuments);

    try {
      ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse1 =
          new ReceiptSummaryQtyByPoAndPoLineResponse("4763030227", 1, 0L);
      List<ReceiptSummaryQtyByPoAndPoLineResponse> receiptSummaryQtyByPoAndPoLineResponses =
          new ArrayList<>();
      receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse1);
      doReturn(receiptSummaryQtyByPoAndPoLineResponses)
          .when(receiptService)
          .receivedQtyInVNPKByPoAndPoLineList(anyList(), anySet());
      doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
          .when(deliveryService)
          .findDeliveryDocument(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              instructionRequest.getUpcNumber(),
              httpHeaders);
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertNull(instructionResponse.getInstruction());
    } catch (ReceivingException receivingException) {
      assert (false);
    }

    deliveryDocuments1.get(0).getDeliveryDocumentLines().get(0).setLimitedQtyVerifiedOn(new Date());
    try {
      doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
          .when(deliveryService)
          .findDeliveryDocument(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              instructionRequest.getUpcNumber(),
              httpHeaders);
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertNull(instructionResponse.getInstruction());
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test
  public void publishWorkingIfNeeded_WRK() {
    when(deliveryStatusPublisher.publishDeliveryStatus(
            95350000L,
            DeliveryStatus.WORKING.toString(),
            null,
            ReceivingUtils.getForwardablHeader(httpHeaders)))
        .thenReturn(null);
    instructionResponse.getDeliveryDocuments().get(0).setDeliveryStatus(DeliveryStatus.WRK);
    instructionService.publishWorkingIfNeeded(instructionResponse, httpHeaders);
    verify(deliveryStatusPublisher, times(0))
        .publishDeliveryStatus(
            95350000L,
            DeliveryStatus.WORKING.toString(),
            null,
            ReceivingUtils.getForwardablHeader(httpHeaders));
  }

  @Test
  public void publishWorkingIfNeeded_ARV() {
    when(deliveryStatusPublisher.publishDeliveryStatus(
            95350000L,
            DeliveryStatus.WORKING.toString(),
            null,
            ReceivingUtils.getForwardablHeader(httpHeaders)))
        .thenReturn(null);
    instructionResponse.getDeliveryDocuments().get(0).setDeliveryStatus(DeliveryStatus.ARV);
    instructionService.publishWorkingIfNeeded(instructionResponse, httpHeaders);
    verify(deliveryStatusPublisher, times(0))
        .publishDeliveryStatus(
            95350000L,
            DeliveryStatus.WORKING.toString(),
            null,
            ReceivingUtils.getForwardablHeader(httpHeaders));
  }

  @Test
  public void publishWorkingIfNeeded_OPN_DOOR_OPEN() {
    when(deliveryStatusPublisher.publishDeliveryStatus(
            95350000L,
            DeliveryStatus.WORKING.toString(),
            null,
            ReceivingUtils.getForwardablHeader(httpHeaders)))
        .thenReturn(null);
    instructionResponse.getDeliveryDocuments().get(0).setDeliveryStatus(DeliveryStatus.OPN);
    instructionResponse
        .getDeliveryDocuments()
        .get(0)
        .setStateReasonCodes(Arrays.asList(DeliveryReasonCodeState.DOOR_OPEN.name()));
    instructionService.publishWorkingIfNeeded(instructionResponse, httpHeaders);
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(
            95350000L,
            DeliveryStatus.WORKING.toString(),
            null,
            ReceivingUtils.getForwardablHeader(httpHeaders));
  }

  @Test
  public void publishWorkingIfNeeded_OPN_EMPTY_REASON_CODE() {
    when(deliveryStatusPublisher.publishDeliveryStatus(
            95350000L,
            DeliveryStatus.WORKING.toString(),
            null,
            ReceivingUtils.getForwardablHeader(httpHeaders)))
        .thenReturn(null);
    instructionResponse.getDeliveryDocuments().get(0).setDeliveryStatus(DeliveryStatus.OPN);
    instructionResponse.getDeliveryDocuments().get(0).setStateReasonCodes(Arrays.asList());
    instructionService.publishWorkingIfNeeded(instructionResponse, httpHeaders);
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(
            95350000L,
            DeliveryStatus.WORKING.toString(),
            null,
            ReceivingUtils.getForwardablHeader(httpHeaders));
  }

  @Test
  public void publishWorkingIfNeeded_OPN_PENDING_PROBLEM() {
    when(deliveryStatusPublisher.publishDeliveryStatus(
            95350000L,
            DeliveryStatus.WORKING.toString(),
            null,
            ReceivingUtils.getForwardablHeader(httpHeaders)))
        .thenReturn(null);
    instructionResponse.getDeliveryDocuments().get(0).setDeliveryStatus(DeliveryStatus.OPN);
    instructionResponse
        .getDeliveryDocuments()
        .get(0)
        .setStateReasonCodes(Arrays.asList(DeliveryReasonCodeState.PENDING_PROBLEM.name()));
    instructionService.publishWorkingIfNeeded(instructionResponse, httpHeaders);
    verify(deliveryStatusPublisher, times(0))
        .publishDeliveryStatus(
            95350000L,
            DeliveryStatus.WORKING.toString(),
            null,
            ReceivingUtils.getForwardablHeader(httpHeaders));
  }

  @Test
  public void publishWorkingIfNeeded_OPN_PENDING_DOCK_TAG() {
    when(deliveryStatusPublisher.publishDeliveryStatus(
            95350000L,
            DeliveryStatus.WORKING.toString(),
            null,
            ReceivingUtils.getForwardablHeader(httpHeaders)))
        .thenReturn(null);
    instructionResponse.getDeliveryDocuments().get(0).setDeliveryStatus(DeliveryStatus.OPN);
    instructionResponse
        .getDeliveryDocuments()
        .get(0)
        .setStateReasonCodes(Arrays.asList(DeliveryReasonCodeState.PENDING_DOCK_TAG.name()));
    instructionService.publishWorkingIfNeeded(instructionResponse, httpHeaders);
    verify(deliveryStatusPublisher, times(0))
        .publishDeliveryStatus(
            95350000L,
            DeliveryStatus.WORKING.toString(),
            null,
            ReceivingUtils.getForwardablHeader(httpHeaders));
  }

  @Test
  public void publishWorkingIfNeeded_OPN_PENDING_PROBLEM_PENDING_DOCK_TAG() {
    when(deliveryStatusPublisher.publishDeliveryStatus(
            95350000L,
            DeliveryStatus.WORKING.toString(),
            null,
            ReceivingUtils.getForwardablHeader(httpHeaders)))
        .thenReturn(null);
    instructionResponse.getDeliveryDocuments().get(0).setDeliveryStatus(DeliveryStatus.OPN);
    instructionResponse
        .getDeliveryDocuments()
        .get(0)
        .setStateReasonCodes(
            Arrays.asList(
                DeliveryReasonCodeState.PENDING_DOCK_TAG.name(),
                DeliveryReasonCodeState.PENDING_PROBLEM.name()));
    instructionService.publishWorkingIfNeeded(instructionResponse, httpHeaders);
    verify(deliveryStatusPublisher, times(0))
        .publishDeliveryStatus(
            95350000L,
            DeliveryStatus.WORKING.toString(),
            null,
            ReceivingUtils.getForwardablHeader(httpHeaders));
  }

  @Test
  public void testServeInstructionRequest_problemTag_not_found() throws ReceivingException {
    try {
      TenantContext.setFacilityCountryCode("US");
      TenantContext.setFacilityNum(32612);

      InstructionRequest instructionRequest1 =
          MockInstruction.getInstructionRequestWithWorkingState();
      instructionRequest1.setProblemTagId("32612760672009");

      doReturn(true).when(configUtils).getConfiguredFeatureFlag("32612", KOTLIN_ENABLED, false);
      doReturn(problemService)
          .when(configUtils)
          .getConfiguredInstance(
              anyString(), eq(ReceivingConstants.PROBLEM_SERVICE), eq(ProblemService.class));
      doThrow(
              new ReceivingException(
                  ReceivingException.PTAG_NOT_FOUND,
                  HttpStatus.NOT_FOUND,
                  ReceivingException.GET_PTAG_ERROR_CODE))
          .when(problemService)
          .getProblemDetails(anyString());

      instructionService.serveInstructionRequest(
          gson.toJson(instructionRequest1), MockHttpHeaders.getHeaders("32612", "US"));
    } catch (ReceivingException e) {
      assertEquals(e.getErrorResponse().getErrorCode(), ReceivingException.GET_PTAG_ERROR_CODE);
      assertEquals(e.getErrorResponse().getErrorMessage(), ReceivingException.PTAG_NOT_FOUND);
    }
  }

  @Test
  public void testServeInstructionRequest_valid_kotlin_problemTag() throws ReceivingException {
    TenantContext.setFacilityNum(32612);
    TenantContext.setFacilityCountryCode("US");
    HttpHeaders headers = MockHttpHeaders.getHeaders("32612", "US");
    ReflectionTestUtils.setField(instructionServiceSpied, "gson", gson);

    InstructionRequest instructionRequest = MockInstruction.getInstructionRequestWithWorkingState();
    instructionRequest.setProblemTagId("32612760672009");

    doReturn(true).when(configUtils).getConfiguredFeatureFlag("32612", KOTLIN_ENABLED, false);
    doReturn(problemService)
        .when(configUtils)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.PROBLEM_SERVICE), eq(ProblemService.class));
    when(problemService.getProblemDetails(anyString()))
        .thenReturn(
            gson.fromJson(
                MockInstruction.getMockProblemLabel().getProblemResponse(),
                FitProblemTagResponse.class));
    when(problemReceivingHelper.isContainerReceivable(any(FitProblemTagResponse.class)))
        .thenReturn(true);
    when(deliveryService.getPOLineInfoFromGDM(
            anyString(), anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(MockGdmResponse.getPOLine());
    when(instructionRepository.findByMessageId(any())).thenReturn(pendingInstruction);

    instructionService.serveInstructionRequest(gson.toJson(instructionRequest), headers);
  }

  @Test
  public void testServePtagInstructionRequest_ForXBlockedItem() throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequestWithWorkingState();
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setOnline(Boolean.FALSE);
    instructionRequest.setMappedFloorLine("RCV100");
    instructionRequest.setOverflowReceiving(Boolean.FALSE);
    instructionRequest.setManualReceivingEnabled(Boolean.FALSE);
    instructionRequest.setProblemTagId("32612760672009");
    HttpHeaders headers = MockHttpHeaders.getHeaders("32899", "US");
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(Integer.valueOf("32899"));
    headers.add(IS_KOTLIN_CLIENT, "true");

    List<DeliveryDocument> multiPODeliveryDocs = MockInstruction.getMultiPoDeliveryDocuments();
    multiPODeliveryDocs.get(0).getDeliveryDocumentLines().get(0).setHandlingCode("X");
    FitProblemTagResponse fitProblemTagResponse =
        gson.fromJson(
            MockInstruction.getMockProblemLabel().getProblemResponse(),
            FitProblemTagResponse.class);

    GdmPOLineResponse gdmPOLineResponse = MockGdmResponse.getPOLine();
    gdmPOLineResponse.setDeliveryDocuments(multiPODeliveryDocs);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.ITEM_X_BLOCKED_VAL))
        .thenReturn(Boolean.TRUE);
    when(configUtils.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.PROBLEM_SERVICE), eq(ProblemService.class)))
        .thenReturn(problemService);
    when(problemService.getProblemDetails(anyString())).thenReturn(fitProblemTagResponse);
    when(problemReceivingHelper.isContainerReceivable(any(FitProblemTagResponse.class)))
        .thenReturn(Boolean.TRUE);
    when(configUtils.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_SERVICE_KEY), eq(DeliveryService.class)))
        .thenReturn(deliveryService);
    when(deliveryService.getPOLineInfoFromGDM(anyString(), anyString(), anyInt(), any()))
        .thenReturn(gdmPOLineResponse);
    try {
      instructionService.servePtagInstructionRequest(instructionRequest, headers);
      fail();
    } catch (ReceivingBadDataException exception) {
      assertEquals(exception.getErrorCode(), ExceptionCodes.ITEM_X_BLOCKED_ERROR);
    }
  }

  @Test
  public void testServeInstructionRequest_problemTag_not_Receivable() throws ReceivingException {
    try {
      TenantContext.setFacilityNum(32612);
      TenantContext.setFacilityCountryCode("US");
      HttpHeaders headers = MockHttpHeaders.getHeaders("32612", "US");
      ReflectionTestUtils.setField(instructionServiceSpied, "gson", gson);

      InstructionRequest instructionRequest =
          MockInstruction.getInstructionRequestWithWorkingState();
      instructionRequest.setProblemTagId("32612760672009");

      doReturn(true).when(configUtils).getConfiguredFeatureFlag("32612", KOTLIN_ENABLED, false);
      doReturn(problemService)
          .when(configUtils)
          .getConfiguredInstance(
              anyString(), eq(ReceivingConstants.PROBLEM_SERVICE), eq(ProblemService.class));
      when(problemService.getProblemDetails(anyString()))
          .thenReturn(
              gson.fromJson(
                  MockInstruction.getMockProblemLabel().getProblemResponse(),
                  FitProblemTagResponse.class));
      when(problemReceivingHelper.isContainerReceivable(any(FitProblemTagResponse.class)))
          .thenReturn(false);

      instructionService.serveInstructionRequest(gson.toJson(instructionRequest), headers);
    } catch (ReceivingConflictException e) {
      assertEquals(e.getErrorCode(), "GLS-RCV-PRBLM-409");
      assertEquals(e.getDescription(), "Problem is not ready to receive.");
    }
  }

  @Test
  public void testServeInstructionRequest_autoSelectLine() throws ReceivingException {
    TenantContext.setFacilityNum(32612);
    TenantContext.setFacilityCountryCode("US");
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());

    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            "32612",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(configUtils.getFeatureFlagsByFacility("32612")).thenReturn(defaultFeatureFlagsByFacility);
    GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
    List<DeliveryDocument> multipleDeliveryDocs = new ArrayList<>();
    multipleDeliveryDocs.add(MockInstruction.getDeliveryDocuments().get(0));
    multipleDeliveryDocs.add(MockInstruction.getDeliveryDocuments().get(0));
    gdmDeliveryDocumentResponse.setDeliveryDocuments(multipleDeliveryDocs);
    doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
        .when(deliveryService)
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    doReturn(null).when(instructionRepository).findByMessageId(anyString());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.KOTLIN_ENABLED, false);
    when(instructionRepository.findByMessageId(any())).thenReturn(pendingInstruction);

    DeliveryDocument deliveryDocument = MockInstruction.getDeliveryDocuments().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    Pair<DeliveryDocument, DeliveryDocumentLine> selectedLine =
        new Pair<>(deliveryDocument, deliveryDocumentLine);
    doReturn(selectedLine)
        .when(defaultDeliveryDocumentSelector)
        .autoSelectDeliveryDocumentLine(anyList());

    InstructionResponse instructionResponse =
        instructionService.serveInstructionRequest(
            gson.toJson(instructionRequest), MockHttpHeaders.getHeaders("32612", "US"));

    assert (instructionResponse.getDeliveryDocuments().size() == 1);
    assertEquals(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getGtin(),
        instructionRequest.getUpcNumber());
    assertEquals(instructionResponse.getInstruction().getId(), pendingInstruction.getId());
  }

  @Test
  public void testServeInstructionRequest_autoSelectLineException() throws ReceivingException {
    try {
      TenantContext.setFacilityNum(32612);
      TenantContext.setFacilityCountryCode("US");
      instructionRequest.setAsnBarcode(null);
      instructionRequest.setUpcNumber("00016017039630");
      instructionRequest.setDeliveryDocuments(null);
      instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());

      doReturn(defaultDeliveryDocumentsSearchHandler)
          .when(configUtils)
          .getConfiguredInstance(
              "32612",
              ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
              DeliveryDocumentsSearchHandler.class);
      when(configUtils.getFeatureFlagsByFacility("32612"))
          .thenReturn(defaultFeatureFlagsByFacility);
      GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
      List<DeliveryDocument> multipleDeliveryDocs = new ArrayList<>();
      multipleDeliveryDocs.add(MockInstruction.getDeliveryDocuments().get(0));
      multipleDeliveryDocs.add(MockInstruction.getDeliveryDocuments().get(0));
      gdmDeliveryDocumentResponse.setDeliveryDocuments(multipleDeliveryDocs);

      doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
          .when(deliveryService)
          .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));

      doReturn(null).when(instructionRepository).findByMessageId(anyString());
      doReturn(null)
          .when(defaultDeliveryDocumentSelector)
          .autoSelectDeliveryDocumentLine(anyList());

      HttpHeaders headers = MockHttpHeaders.getHeaders("32612", "US");
      instructionService.serveInstructionRequest(gson.toJson(instructionRequest), headers);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), "GLS-RCV-PO-LINE-NO-OPEN-QTY-400");
      assertEquals(e.getDescription(), "Allowed PO Line quantity has been received.");
    }
  }

  @Test
  public void
      testServeInstructionRequest_autoSelectLineException_KotlinClient_OverageInstructionCode()
          throws ReceivingException {
    TenantContext.setFacilityNum(32612);
    TenantContext.setFacilityCountryCode("US");
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());

    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            "32612",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(configUtils.getFeatureFlagsByFacility("32612")).thenReturn(defaultFeatureFlagsByFacility);
    GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
    List<DeliveryDocument> multipleDeliveryDocs = new ArrayList<>();
    multipleDeliveryDocs.add(MockInstruction.getDeliveryDocuments().get(0));
    multipleDeliveryDocs.add(MockInstruction.getDeliveryDocuments().get(0));
    gdmDeliveryDocumentResponse.setDeliveryDocuments(multipleDeliveryDocs);

    doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
        .when(deliveryService)
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));

    doReturn(null).when(instructionRepository).findByMessageId(anyString());
    doReturn(null).when(defaultDeliveryDocumentSelector).autoSelectDeliveryDocumentLine(anyList());

    HttpHeaders headers = MockHttpHeaders.getHeaders("32612", "US");
    headers.set(ReceivingConstants.IS_KOTLIN_CLIENT, ReceivingConstants.TRUE_STRING);
    InstructionResponse response =
        instructionService.serveInstructionRequest(gson.toJson(instructionRequest), headers);
    assertNotNull(response.getDeliveryDocuments());
    assertEquals(
        response.getInstruction().getInstructionCode(), ReportingConstants.CC_OVERAGE_PALLET);
  }

  @Test
  public void testServeInstructionRequest_FetchExistingInstruction() throws ReceivingException {
    TenantContext.setFacilityNum(32612);
    TenantContext.setFacilityCountryCode("US");
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());

    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            "32612",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(configUtils.getFeatureFlagsByFacility("32612")).thenReturn(defaultFeatureFlagsByFacility);
    GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
    List<DeliveryDocument> multipleDeliveryDocs = new ArrayList<>();
    multipleDeliveryDocs.add(MockInstruction.getDeliveryDocuments().get(0));
    gdmDeliveryDocumentResponse.setDeliveryDocuments(multipleDeliveryDocs);
    doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
        .when(deliveryService)
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    doReturn(null).when(instructionRepository).findByMessageId(anyString());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.KOTLIN_ENABLED, false);
    when(instructionRepository.findByMessageId(any())).thenReturn(pendingInstruction);

    DeliveryDocument deliveryDocument = MockInstruction.getDeliveryDocuments().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    Pair<DeliveryDocument, DeliveryDocumentLine> selectedLine =
        new Pair<>(deliveryDocument, deliveryDocumentLine);
    doReturn(selectedLine)
        .when(defaultDeliveryDocumentSelector)
        .autoSelectDeliveryDocumentLine(anyList());

    InstructionResponse instructionResponse =
        instructionService.serveInstructionRequest(
            gson.toJson(instructionRequest), MockHttpHeaders.getHeaders("32612", "US"));

    assert (instructionResponse.getDeliveryDocuments().size() == 1);
    DeliveryDocumentLine documentLine =
        instructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);

    assertEquals(documentLine.getGtin(), instructionRequest.getUpcNumber());
    assertEquals(instructionResponse.getInstruction().getId(), pendingInstruction.getId());
    assertEquals(documentLine.getTotalReceivedQty().intValue(), 10);
  }

  @Test
  public void testServeInstructionRequest_autoSelectLineDisabled() throws ReceivingException {
    TenantContext.setFacilityNum(32612);
    TenantContext.setFacilityCountryCode("US");
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());

    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            "32612",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(configUtils.getFeatureFlagsByFacility("32612")).thenReturn(defaultFeatureFlagsByFacility);
    GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
    List<DeliveryDocument> multipleDeliveryDocs = new ArrayList<>();
    multipleDeliveryDocs.add(MockInstruction.getDeliveryDocuments().get(0));
    multipleDeliveryDocs.add(MockInstruction.getDeliveryDocuments().get(0));
    gdmDeliveryDocumentResponse.setDeliveryDocuments(multipleDeliveryDocs);
    doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
        .when(deliveryService)
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    doReturn(null).when(instructionRepository).findByMessageId(anyString());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.KOTLIN_ENABLED, false);
    when(instructionRepository.findByMessageId(any())).thenReturn(pendingInstruction);

    DeliveryDocument deliveryDocument = MockInstruction.getDeliveryDocuments().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    Pair<DeliveryDocument, DeliveryDocumentLine> selectedLine =
        new Pair<>(deliveryDocument, deliveryDocumentLine);
    doReturn(selectedLine)
        .when(defaultDeliveryDocumentSelector)
        .autoSelectDeliveryDocumentLine(anyList());
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), IS_AUTO_SELECT_LINE_DISABLED, false))
        .thenReturn(TRUE);

    InstructionResponse instructionResponse =
        instructionService.serveInstructionRequest(
            gson.toJson(instructionRequest), MockHttpHeaders.getHeaders("32612", "US"));

    verify(defaultDeliveryDocumentSelector, times(0)).autoSelectDeliveryDocumentLine(any());
    assertEquals(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getGtin(),
        instructionRequest.getUpcNumber());
  }

  @Test
  public void testServeInstructionRequest_autoSelectLineDisabled_MANUAL_PO_SELECTION_CODE_ENABLED()
      throws ReceivingException {
    TenantContext.setFacilityNum(32612);
    TenantContext.setFacilityCountryCode("US");
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());

    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            "32612",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(configUtils.getFeatureFlagsByFacility("32612")).thenReturn(defaultFeatureFlagsByFacility);
    GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
    List<DeliveryDocument> multipleDeliveryDocs = new ArrayList<>();
    multipleDeliveryDocs.add(MockInstruction.getDeliveryDocuments().get(0));
    multipleDeliveryDocs.add(MockInstruction.getDeliveryDocuments().get(0));
    gdmDeliveryDocumentResponse.setDeliveryDocuments(multipleDeliveryDocs);
    doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
        .when(deliveryService)
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    doReturn(null).when(instructionRepository).findByMessageId(anyString());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.KOTLIN_ENABLED, false);
    when(instructionRepository.findByMessageId(any())).thenReturn(pendingInstruction);

    DeliveryDocument deliveryDocument = MockInstruction.getDeliveryDocuments().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    Pair<DeliveryDocument, DeliveryDocumentLine> selectedLine =
        new Pair<>(deliveryDocument, deliveryDocumentLine);
    doReturn(selectedLine)
        .when(defaultDeliveryDocumentSelector)
        .autoSelectDeliveryDocumentLine(anyList());

    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), IS_AUTO_SELECT_LINE_DISABLED, false))
        .thenReturn(TRUE);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), IS_MANUAL_PO_SELECTION_CODE_ENABLED, false))
        .thenReturn(TRUE);

    InstructionResponse instructionResponse =
        instructionService.serveInstructionRequest(
            gson.toJson(instructionRequest), MockHttpHeaders.getHeaders("32612", "US"));

    final Instruction instruction = instructionResponse.getInstruction();
    assertNotNull(instruction);
    assertEquals(instruction.getInstructionCode(), MANUAL_PO_SELECTION);
  }

  @Test
  public void testServeInstructionRequest_autoSelectLineDisabled_MANUAL_PO_SELECTION_CODE_DIABLED()
      throws ReceivingException {
    TenantContext.setFacilityNum(32612);
    TenantContext.setFacilityCountryCode("US");
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());

    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            "32612",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(configUtils.getFeatureFlagsByFacility("32612")).thenReturn(defaultFeatureFlagsByFacility);
    GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
    List<DeliveryDocument> multipleDeliveryDocs = new ArrayList<>();
    multipleDeliveryDocs.add(MockInstruction.getDeliveryDocuments().get(0));
    multipleDeliveryDocs.add(MockInstruction.getDeliveryDocuments().get(0));
    gdmDeliveryDocumentResponse.setDeliveryDocuments(multipleDeliveryDocs);
    doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
        .when(deliveryService)
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    doReturn(null).when(instructionRepository).findByMessageId(anyString());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.KOTLIN_ENABLED, false);
    when(instructionRepository.findByMessageId(any())).thenReturn(pendingInstruction);

    DeliveryDocument deliveryDocument = MockInstruction.getDeliveryDocuments().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    Pair<DeliveryDocument, DeliveryDocumentLine> selectedLine =
        new Pair<>(deliveryDocument, deliveryDocumentLine);
    doReturn(selectedLine)
        .when(defaultDeliveryDocumentSelector)
        .autoSelectDeliveryDocumentLine(anyList());

    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), IS_AUTO_SELECT_LINE_DISABLED, false))
        .thenReturn(TRUE);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), IS_MANUAL_PO_SELECTION_CODE_ENABLED, false))
        .thenReturn(FALSE);

    InstructionResponse instructionResponse =
        instructionService.serveInstructionRequest(
            gson.toJson(instructionRequest), MockHttpHeaders.getHeaders("32612", "US"));

    final Instruction instruction = instructionResponse.getInstruction();
    assertNull(instruction);
  }

  @Test
  public void testServeInstructionRequest_withOCC_NullinGDMResponse() {
    List<DeliveryDocument> deliveryDocuments1 = MockInstruction.getDeliveryDocuments();
    reset(deliveryService);
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00799366522591");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    ReflectionTestUtils.setField(instructionServiceSpied, "gson", gson);
    doReturn(1l).when(receiptService).getReceivedQtyByPoAndPoLine("4763030227", 1);
    when(instructionRepository.findByMessageId(any())).thenReturn(null);
    when(instructionRepository.save(any())).thenReturn(pendingInstruction);
    when(configUtils.isFeatureFlagEnabled(anyString())).thenReturn(Boolean.TRUE);

    try {
      doReturn(defaultFeatureFlagsByFacility).when(configUtils).getFeatureFlagsByFacility("32987");
      GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
      List<DeliveryDocument> oneDeliveryDocuments = new ArrayList<>();
      oneDeliveryDocuments.add(deliveryDocuments1.get(0));
      gdmDeliveryDocumentResponse.setDeliveryDocuments(oneDeliveryDocuments);
      when(deliveryItemOverrideService.findByDeliveryNumberAndItemNumber(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              gdmDeliveryDocumentResponse
                  .getDeliveryDocuments()
                  .get(0)
                  .getDeliveryDocumentLines()
                  .get(0)
                  .getItemNbr()))
          .thenReturn(Optional.empty());
      when(configUtils.isFeatureFlagEnabled(ReceivingConstants.IS_STORAGE_CHECK_ENABLED))
          .thenReturn(false);
      doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
          .when(deliveryService)
          .findDeliveryDocument(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              instructionRequest.getUpcNumber(),
              httpHeaders);
      doReturn(gson.toJson(fdeCreateContainerResponse)).when(fdeService).receive(any(), any());
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getGtin(),
          instructionRequest.getUpcNumber());
      assertEquals(
          Boolean.FALSE,
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getFirstExpiryFirstOut());
      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getWarehouseMinLifeRemainingToReceive(),
          deliveryDocuments1
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getWarehouseMinLifeRemainingToReceive());
      assert (true);
    } catch (ReceivingException receivingException) {
      assert (true);
    }
  }

  @Test
  public void testServeInstructionRequest_withOCCAndPackAck() {
    List<DeliveryDocument> deliveryDocuments1 =
        MockInstruction.getDeliveryDocumentsWithCountryCode();
    reset(deliveryService);
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    Map<String, String> itemMiscInfo = new HashMap<>();
    itemMiscInfo.put(ReceivingConstants.IS_OCC_ACK, "true");
    itemMiscInfo.put(ReceivingConstants.IS_PACK_ACK, "true");
    DeliveryItemOverride deliveryItemOverride = new DeliveryItemOverride();
    deliveryItemOverride.setDeliveryNumber(12345L);
    deliveryItemOverride.setItemNumber(567892L);
    deliveryItemOverride.setTempPalletTi(6);
    deliveryItemOverride.setTempPalletTi(1);
    deliveryItemOverride.setItemMiscInfo(itemMiscInfo);
    ReflectionTestUtils.setField(instructionServiceSpied, "gson", gson);
    doReturn(1l).when(receiptService).getReceivedQtyByPoAndPoLine("4763030227", 1);
    when(instructionRepository.findByMessageId(any())).thenReturn(null);
    when(instructionRepository.save(any())).thenReturn(pendingInstruction);
    when(configUtils.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED))
        .thenReturn(true);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.IS_VALIDATE_PACK_TYPE_ACK_ENABLED))
        .thenReturn(true);

    try {
      doReturn(defaultFeatureFlagsByFacility).when(configUtils).getFeatureFlagsByFacility("32987");
      GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
      List<DeliveryDocument> oneDeliveryDocuments = new ArrayList<>();
      oneDeliveryDocuments.add(deliveryDocuments1.get(0));
      gdmDeliveryDocumentResponse.setDeliveryDocuments(oneDeliveryDocuments);

      doReturn(Optional.of(deliveryItemOverride))
          .when(deliveryItemOverrideService)
          .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
      doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
          .when(deliveryService)
          .findDeliveryDocument(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              instructionRequest.getUpcNumber(),
              httpHeaders);
      doReturn(gson.toJson(fdeCreateContainerResponse)).when(fdeService).receive(any(), any());
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getGtin(),
          instructionRequest.getUpcNumber());
      assertEquals(
          Boolean.FALSE,
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getFirstExpiryFirstOut());
      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getWarehouseMinLifeRemainingToReceive(),
          deliveryDocuments1
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getWarehouseMinLifeRemainingToReceive());
      assert (true);
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test
  public void testServeInstructionRequest_withOCCAndPackNotAck() {
    List<DeliveryDocument> deliveryDocuments1 =
        MockInstruction.getDeliveryDocumentsWithCountryCode();
    reset(deliveryService);
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    Map<String, String> itemMiscInfo = new HashMap<>();
    itemMiscInfo.put(ReceivingConstants.IS_OCC_ACK, "false");
    itemMiscInfo.put(ReceivingConstants.IS_OCC_CONDITIONAL_ACK, "false");
    itemMiscInfo.put(ReceivingConstants.IS_PACK_ACK, "false");
    DeliveryItemOverride deliveryItemOverride = new DeliveryItemOverride();
    deliveryItemOverride.setDeliveryNumber(12345L);
    deliveryItemOverride.setItemNumber(567892L);
    deliveryItemOverride.setTempPalletTi(6);
    deliveryItemOverride.setTempPalletTi(1);
    deliveryItemOverride.setItemMiscInfo(itemMiscInfo);
    ReflectionTestUtils.setField(instructionServiceSpied, "gson", gson);
    doReturn(1l).when(receiptService).getReceivedQtyByPoAndPoLine("4763030227", 1);
    when(instructionRepository.findByMessageId(any())).thenReturn(null);
    when(instructionRepository.save(any())).thenReturn(pendingInstruction);
    when(configUtils.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED))
        .thenReturn(true);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.IS_VALIDATE_PACK_TYPE_ACK_ENABLED))
        .thenReturn(true);

    try {
      doReturn(defaultFeatureFlagsByFacility).when(configUtils).getFeatureFlagsByFacility("32987");
      GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
      List<DeliveryDocument> oneDeliveryDocuments = new ArrayList<>();
      oneDeliveryDocuments.add(deliveryDocuments1.get(0));
      gdmDeliveryDocumentResponse.setDeliveryDocuments(oneDeliveryDocuments);

      doReturn(Optional.of(deliveryItemOverride))
          .when(deliveryItemOverrideService)
          .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
      doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
          .when(deliveryService)
          .findDeliveryDocument(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              instructionRequest.getUpcNumber(),
              httpHeaders);
      doReturn(gson.toJson(fdeCreateContainerResponse)).when(fdeService).receive(any(), any());
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getGtin(),
          instructionRequest.getUpcNumber());
      assertEquals(
          Boolean.FALSE,
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getFirstExpiryFirstOut());
      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getWarehouseMinLifeRemainingToReceive(),
          deliveryDocuments1
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getWarehouseMinLifeRemainingToReceive());
      assert (true);
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test
  public void testServeInstructionRequest_withPackNotAck() {
    List<DeliveryDocument> deliveryDocuments1 =
        MockInstruction.getDeliveryDocumentsWithCountryCode();
    reset(deliveryService);
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    Map<String, String> itemMiscInfo = new HashMap<>();
    itemMiscInfo.put(ReceivingConstants.IS_OCC_ACK, "true");
    itemMiscInfo.put(ReceivingConstants.IS_PACK_ACK, "false");
    DeliveryItemOverride deliveryItemOverride = new DeliveryItemOverride();
    deliveryItemOverride.setDeliveryNumber(12345L);
    deliveryItemOverride.setItemNumber(567892L);
    deliveryItemOverride.setTempPalletTi(6);
    deliveryItemOverride.setTempPalletTi(1);
    deliveryItemOverride.setItemMiscInfo(itemMiscInfo);
    ReflectionTestUtils.setField(instructionServiceSpied, "gson", gson);
    doReturn(1l).when(receiptService).getReceivedQtyByPoAndPoLine("4763030227", 1);
    when(instructionRepository.findByMessageId(any())).thenReturn(null);
    when(instructionRepository.save(any())).thenReturn(pendingInstruction);
    when(configUtils.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED))
        .thenReturn(true);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.IS_VALIDATE_PACK_TYPE_ACK_ENABLED))
        .thenReturn(true);

    try {
      doReturn(defaultFeatureFlagsByFacility).when(configUtils).getFeatureFlagsByFacility("32987");
      GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
      List<DeliveryDocument> oneDeliveryDocuments = new ArrayList<>();
      oneDeliveryDocuments.add(deliveryDocuments1.get(0));
      gdmDeliveryDocumentResponse.setDeliveryDocuments(oneDeliveryDocuments);

      doReturn(Optional.of(deliveryItemOverride))
          .when(deliveryItemOverrideService)
          .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
      doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
          .when(deliveryService)
          .findDeliveryDocument(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              instructionRequest.getUpcNumber(),
              httpHeaders);
      doReturn(gson.toJson(fdeCreateContainerResponse)).when(fdeService).receive(any(), any());
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getGtin(),
          instructionRequest.getUpcNumber());
      assertEquals(
          Boolean.FALSE,
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getFirstExpiryFirstOut());
      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getWarehouseMinLifeRemainingToReceive(),
          deliveryDocuments1
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getWarehouseMinLifeRemainingToReceive());
      assert (true);
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test
  public void testServeInstructionRequest_withOnlyOCCEnabled() {
    List<DeliveryDocument> deliveryDocuments1 =
        MockInstruction.getDeliveryDocumentsWithCountryCode();
    reset(deliveryService);
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    Map<String, String> itemMiscInfo = new HashMap<>();
    itemMiscInfo.put(ReceivingConstants.IS_OCC_ACK, "true");
    DeliveryItemOverride deliveryItemOverride = new DeliveryItemOverride();
    deliveryItemOverride.setDeliveryNumber(12345L);
    deliveryItemOverride.setItemNumber(567892L);
    deliveryItemOverride.setTempPalletTi(6);
    deliveryItemOverride.setTempPalletTi(1);
    deliveryItemOverride.setItemMiscInfo(itemMiscInfo);
    ReflectionTestUtils.setField(instructionServiceSpied, "gson", gson);
    doReturn(1l).when(receiptService).getReceivedQtyByPoAndPoLine("4763030227", 1);
    when(instructionRepository.findByMessageId(any())).thenReturn(null);
    when(instructionRepository.save(any())).thenReturn(pendingInstruction);
    when(configUtils.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED))
        .thenReturn(true);

    try {
      doReturn(defaultFeatureFlagsByFacility).when(configUtils).getFeatureFlagsByFacility("32987");
      GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
      List<DeliveryDocument> oneDeliveryDocuments = new ArrayList<>();
      oneDeliveryDocuments.add(deliveryDocuments1.get(0));
      gdmDeliveryDocumentResponse.setDeliveryDocuments(oneDeliveryDocuments);

      doReturn(Optional.of(deliveryItemOverride))
          .when(deliveryItemOverrideService)
          .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
      doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
          .when(deliveryService)
          .findDeliveryDocument(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              instructionRequest.getUpcNumber(),
              httpHeaders);
      doReturn(gson.toJson(fdeCreateContainerResponse)).when(fdeService).receive(any(), any());
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getGtin(),
          instructionRequest.getUpcNumber());
      assertEquals(
          Boolean.FALSE,
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getFirstExpiryFirstOut());
      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getWarehouseMinLifeRemainingToReceive(),
          deliveryDocuments1
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getWarehouseMinLifeRemainingToReceive());
      assert (true);
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test
  public void testServeInstructionRequest_withOnlyPackAckEnabled() {
    List<DeliveryDocument> deliveryDocuments1 =
        MockInstruction.getDeliveryDocumentsWithCountryCode();
    reset(deliveryService);
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    Map<String, String> itemMiscInfo = new HashMap<>();
    itemMiscInfo.put(ReceivingConstants.IS_PACK_ACK, "true");
    DeliveryItemOverride deliveryItemOverride = new DeliveryItemOverride();
    deliveryItemOverride.setDeliveryNumber(12345L);
    deliveryItemOverride.setItemNumber(567892L);
    deliveryItemOverride.setTempPalletTi(6);
    deliveryItemOverride.setTempPalletTi(1);
    deliveryItemOverride.setItemMiscInfo(itemMiscInfo);
    ReflectionTestUtils.setField(instructionServiceSpied, "gson", gson);
    doReturn(1l).when(receiptService).getReceivedQtyByPoAndPoLine("4763030227", 1);
    when(instructionRepository.findByMessageId(any())).thenReturn(null);
    when(instructionRepository.save(any())).thenReturn(pendingInstruction);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.IS_VALIDATE_PACK_TYPE_ACK_ENABLED))
        .thenReturn(true);

    try {
      doReturn(defaultFeatureFlagsByFacility).when(configUtils).getFeatureFlagsByFacility("32987");
      GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
      List<DeliveryDocument> oneDeliveryDocuments = new ArrayList<>();
      oneDeliveryDocuments.add(deliveryDocuments1.get(0));
      gdmDeliveryDocumentResponse.setDeliveryDocuments(oneDeliveryDocuments);

      doReturn(Optional.of(deliveryItemOverride))
          .when(deliveryItemOverrideService)
          .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
      doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
          .when(deliveryService)
          .findDeliveryDocument(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              instructionRequest.getUpcNumber(),
              httpHeaders);
      doReturn(gson.toJson(fdeCreateContainerResponse)).when(fdeService).receive(any(), any());
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getGtin(),
          instructionRequest.getUpcNumber());
      assertEquals(
          Boolean.FALSE,
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getFirstExpiryFirstOut());
      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getWarehouseMinLifeRemainingToReceive(),
          deliveryDocuments1
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getWarehouseMinLifeRemainingToReceive());
      assert (true);
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test
  public void testServeInstructionRequest_withConditionalOCC() {
    List<DeliveryDocument> deliveryDocuments1 =
        MockInstruction.getDeliveryDocumentsWithCountryCode();
    reset(deliveryService);
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    Map<String, String> itemMiscInfo = new HashMap<>();
    itemMiscInfo.put(ReceivingConstants.IS_OCC_ACK, "false");
    itemMiscInfo.put(ReceivingConstants.IS_OCC_CONDITIONAL_ACK, "true");
    DeliveryItemOverride deliveryItemOverride = new DeliveryItemOverride();
    deliveryItemOverride.setDeliveryNumber(12345L);
    deliveryItemOverride.setItemNumber(567892L);
    deliveryItemOverride.setTempPalletTi(6);
    deliveryItemOverride.setTempPalletTi(1);
    deliveryItemOverride.setItemMiscInfo(itemMiscInfo);
    ReflectionTestUtils.setField(instructionServiceSpied, "gson", gson);
    doReturn(1l).when(receiptService).getReceivedQtyByPoAndPoLine("4763030227", 1);
    when(instructionRepository.findByMessageId(any())).thenReturn(null);
    when(instructionRepository.save(any())).thenReturn(pendingInstruction);
    when(configUtils.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED))
        .thenReturn(true);

    try {
      doReturn(defaultFeatureFlagsByFacility).when(configUtils).getFeatureFlagsByFacility("32987");
      GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
      List<DeliveryDocument> oneDeliveryDocuments = new ArrayList<>();
      oneDeliveryDocuments.add(deliveryDocuments1.get(0));
      gdmDeliveryDocumentResponse.setDeliveryDocuments(oneDeliveryDocuments);

      doReturn(Optional.of(deliveryItemOverride))
          .when(deliveryItemOverrideService)
          .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
      doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
          .when(deliveryService)
          .findDeliveryDocument(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              instructionRequest.getUpcNumber(),
              httpHeaders);
      doReturn(gson.toJson(fdeCreateContainerResponse)).when(fdeService).receive(any(), any());
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getGtin(),
          instructionRequest.getUpcNumber());
      assertEquals(
          Boolean.FALSE,
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getFirstExpiryFirstOut());
      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getWarehouseMinLifeRemainingToReceive(),
          deliveryDocuments1
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getWarehouseMinLifeRemainingToReceive());
      assert (true);
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test
  public void testServeInstructionRequest_withisItemValidationDone() {
    List<DeliveryDocument> deliveryDocuments1 =
        MockInstruction.getDeliveryDocumentsWithCountryCode();
    reset(deliveryService);
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setItemValidationDone(true);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    Map<String, String> itemMiscInfo = new HashMap<>();
    itemMiscInfo.put(ReceivingConstants.IS_OCC_ACK, "true");
    DeliveryItemOverride deliveryItemOverride = new DeliveryItemOverride();
    deliveryItemOverride.setDeliveryNumber(12345L);
    deliveryItemOverride.setItemNumber(567892L);
    deliveryItemOverride.setTempPalletTi(6);
    deliveryItemOverride.setTempPalletTi(1);
    deliveryItemOverride.setItemMiscInfo(itemMiscInfo);
    ReflectionTestUtils.setField(instructionServiceSpied, "gson", gson);
    doReturn(1l).when(receiptService).getReceivedQtyByPoAndPoLine("4763030227", 1);
    when(instructionRepository.findByMessageId(any())).thenReturn(null);
    when(instructionRepository.save(any())).thenReturn(pendingInstruction);
    when(configUtils.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED))
        .thenReturn(true);

    try {
      doReturn(defaultFeatureFlagsByFacility).when(configUtils).getFeatureFlagsByFacility("32987");
      GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
      List<DeliveryDocument> oneDeliveryDocuments = new ArrayList<>();
      oneDeliveryDocuments.add(deliveryDocuments1.get(0));
      gdmDeliveryDocumentResponse.setDeliveryDocuments(oneDeliveryDocuments);

      doReturn(Optional.of(deliveryItemOverride))
          .when(deliveryItemOverrideService)
          .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
      doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
          .when(deliveryService)
          .findDeliveryDocument(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              instructionRequest.getUpcNumber(),
              httpHeaders);
      doReturn(gson.toJson(fdeCreateContainerResponse)).when(fdeService).receive(any(), any());
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getGtin(),
          instructionRequest.getUpcNumber());
      assertEquals(
          Boolean.FALSE,
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getFirstExpiryFirstOut());
      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getWarehouseMinLifeRemainingToReceive(),
          deliveryDocuments1
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getWarehouseMinLifeRemainingToReceive());
      assert (true);
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test
  public void testServeInstructionRequest_withOCCenabledButNotAck() {
    List<DeliveryDocument> deliveryDocuments1 =
        MockInstruction.getDeliveryDocumentsWithCountryCode();
    reset(deliveryService);
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00016017039630");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    Map<String, String> itemMiscInfo = new HashMap<>();
    itemMiscInfo.put(ReceivingConstants.IS_OCC_ACK, "false");
    itemMiscInfo.put(ReceivingConstants.IS_OCC_CONDITIONAL_ACK, "false");
    DeliveryItemOverride deliveryItemOverride = new DeliveryItemOverride();
    deliveryItemOverride.setDeliveryNumber(12345L);
    deliveryItemOverride.setItemNumber(567892L);
    deliveryItemOverride.setTempPalletTi(6);
    deliveryItemOverride.setTempPalletTi(1);
    deliveryItemOverride.setItemMiscInfo(itemMiscInfo);
    ReflectionTestUtils.setField(instructionServiceSpied, "gson", gson);
    doReturn(1l).when(receiptService).getReceivedQtyByPoAndPoLine("4763030227", 1);
    when(instructionRepository.findByMessageId(any())).thenReturn(null);
    when(instructionRepository.save(any())).thenReturn(pendingInstruction);
    when(configUtils.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED))
        .thenReturn(true);

    try {
      doReturn(defaultFeatureFlagsByFacility).when(configUtils).getFeatureFlagsByFacility("32987");
      GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
      List<DeliveryDocument> oneDeliveryDocuments = new ArrayList<>();
      oneDeliveryDocuments.add(deliveryDocuments1.get(0));
      gdmDeliveryDocumentResponse.setDeliveryDocuments(oneDeliveryDocuments);

      doReturn(Optional.of(deliveryItemOverride))
          .when(deliveryItemOverrideService)
          .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
      doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
          .when(deliveryService)
          .findDeliveryDocument(
              Long.parseLong(instructionRequest.getDeliveryNumber()),
              instructionRequest.getUpcNumber(),
              httpHeaders);
      doReturn(gson.toJson(fdeCreateContainerResponse)).when(fdeService).receive(any(), any());
      InstructionResponse instructionResponse =
          instructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
      assert (instructionResponse.getDeliveryDocuments().size() == 1);
      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getGtin(),
          instructionRequest.getUpcNumber());
      assertEquals(
          Boolean.FALSE,
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getFirstExpiryFirstOut());
      assertEquals(
          instructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getWarehouseMinLifeRemainingToReceive(),
          deliveryDocuments1
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getWarehouseMinLifeRemainingToReceive());
      assert (true);
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }
}
