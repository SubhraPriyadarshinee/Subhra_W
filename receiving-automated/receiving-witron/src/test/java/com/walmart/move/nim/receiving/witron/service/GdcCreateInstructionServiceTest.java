package com.walmart.move.nim.receiving.witron.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.common.validators.DeliveryValidator;
import com.walmart.move.nim.receiving.core.common.validators.PurchaseReferenceValidator;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.helper.ProblemReceivingHelper;
import com.walmart.move.nim.receiving.core.item.rules.LimitedQtyRule;
import com.walmart.move.nim.receiving.core.item.rules.LithiumIonLimitedQtyRule;
import com.walmart.move.nim.receiving.core.item.rules.LithiumIonRule;
import com.walmart.move.nim.receiving.core.item.rules.RuleSet;
import com.walmart.move.nim.receiving.core.message.publisher.JMSInstructionPublisher;
import com.walmart.move.nim.receiving.core.message.publisher.MessagePublisher;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliveryDocumentResponse;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.*;
import com.walmart.move.nim.receiving.witron.builder.ContainerLabelBuilder;
import com.walmart.move.nim.receiving.witron.common.GDCFlagReader;
import com.walmart.move.nim.receiving.witron.mock.data.MockGdmResponse;
import com.walmart.move.nim.receiving.witron.mock.data.MockInstruction;
import java.io.File;
import java.io.IOException;
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

public class GdcCreateInstructionServiceTest extends ReceivingTestBase {
  @InjectMocks private GdcInstructionService gdcInstructionService;
  @InjectMocks @Spy private DeliveryDocumentHelper deliveryDocumentHelper;
  @InjectMocks private ManualInstructionService manualInstructionService;
  @Mock private InstructionHelperService instructionHelperService;
  @InjectMocks private JMSInstructionPublisher jmsInstructionPublisher;
  @Mock private ContainerService containerService;
  @InjectMocks private RegulatedItemService regulatedItemService;
  @Spy private GdcInstructionService gdcInstructionServiceSpied;
  @Mock private AppConfig appConfig;
  @Mock private FdeService fdeService;
  @Mock private MovePublisher movePublisher;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private ReceiptService receiptService;
  @Mock private DeliveryServiceImpl deliveryService;
  @Mock private InstructionRepository instructionRepository;
  @Mock private DeliveryStatusPublisher deliveryStatusPublisher;
  @Mock private DeliveryItemOverrideService deliveryItemOverrideService;
  @Mock private GdcSlottingServiceImpl slottingService;
  @Mock private ContainerLabelBuilder containerLabelBuilder;
  @Mock private DeliveryValidator deliveryValidator;
  @Mock private PurchaseReferenceValidator purchaseReferenceValidator;
  @Mock private ProblemService problemService;
  @Mock private ProblemReceivingHelper problemReceivingHelper;
  @Mock private DefaultSorterPublisher defaultSorterPublisher;
  @Mock private DefaultDeliveryDocumentSelector defaultDeliveryDocumentSelector;
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private LPNCacheService lpnCacheService;
  @Mock private GDCFlagReader gdcFlagReader;

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
    ReflectionTestUtils.setField(gdcInstructionService, "containerService", containerService);
    ReflectionTestUtils.setField(
        gdcInstructionService, "purchaseReferenceValidator", purchaseReferenceValidator);
    ReflectionTestUtils.setField(instructionPersisterService, "containerService", containerService);
    ReflectionTestUtils.setField(instructionHelperService, "containerService", containerService);
    ReflectionTestUtils.setField(
        gdcInstructionService, "instructionPersisterService", instructionPersisterService);
    ReflectionTestUtils.setField(
        gdcInstructionService, "manualInstructionService", manualInstructionService);
    ReflectionTestUtils.setField(
        gdcInstructionService, "instructionHelperService", instructionHelperService);
    ReflectionTestUtils.setField(
        manualInstructionService, "instructionHelperService", instructionHelperService);
    ReflectionTestUtils.setField(
        manualInstructionService, "deliveryDocumentHelper", deliveryDocumentHelper);
    ReflectionTestUtils.setField(
        manualInstructionService, "instructionPersisterService", instructionPersisterService);
    ReflectionTestUtils.setField(
        manualInstructionService, "tenantSpecificConfigReader", configUtils);
    ReflectionTestUtils.setField(gdcInstructionService, "gson", gson);
    ReflectionTestUtils.setField(defaultDeliveryDocumentsSearchHandler, "gson", gson);
    ReflectionTestUtils.setField(gdcInstructionServiceSpied, "configUtils", configUtils);
    ReflectionTestUtils.setField(gdcInstructionService, "itemCategoryRuleSet", itemCategoryRuleSet);
    ReflectionTestUtils.setField(
        gdcInstructionService, "regulatedItemService", regulatedItemService);
    ReflectionTestUtils.setField(regulatedItemService, "itemCategoryRuleSet", itemCategoryRuleSet);
    try {

      Gson gsonWithDateFormat = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").create();

      File resource = new ClassPathResource("instruction_response.json").getFile();
      String data = new String(Files.readAllBytes(resource.toPath()));

      instructionResponse = gsonWithDateFormat.fromJson(data, InstructionResponseImplNew.class);
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
            String.valueOf(getFacilityNum()),
            ReceivingConstants.INSTRUCTION_PUBLISHER,
            MessagePublisher.class))
        .thenReturn(jmsInstructionPublisher);
    when(configUtils.getConfiguredInstance(
            String.valueOf(getFacilityNum()),
            ReceivingConstants.SORTER_PUBLISHER,
            SorterPublisher.class))
        .thenReturn(defaultSorterPublisher);
    when(configUtils.getCcmValue(
            TenantContext.getFacilityNum(),
            ELIGIBLE_TRANSFER_POS_CCM_CONFIG,
            DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE))
        .thenReturn("28");
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
    reset(deliveryValidator);
    reset(gdcFlagReader);
  }

  @Test
  public void testServeInstructionRequest_whenUpcIsEmpty() {
    instructionError = InstructionErrorCode.getErrorValue(ReceivingException.NO_UPC_ERROR);
    try {
      when(configUtils.getFeatureFlagsByFacility("32987"))
          .thenReturn(defaultFeatureFlagsByFacility);
      instructionRequest.setUpcNumber("");
      gdcInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    } catch (ReceivingException receivingException) {
      assert (true);
      assertEquals(
          receivingException.getErrorResponse().getErrorMessage(),
          instructionError.getErrorMessage());
    }
  }

  @Test
  public void testServeInstructionRequest_validateItemMinLifeRemaining() throws ReceivingException {
    try {
      doReturn(defaultDeliveryDocumentsSearchHandler)
          .when(configUtils)
          .getConfiguredInstance(
              "32987",
              ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
              DeliveryDocumentsSearchHandler.class);
      List<DeliveryDocument> deliveryDocs =
          MockGdmResponse.getGdmResponseWithInvalidItemInfo().getDeliveryDocuments();

      when(configUtils.getFeatureFlagsByFacility("32987"))
          .thenReturn(defaultFeatureFlagsByFacility);
      when(deliveryService.findDeliveryDocument(anyLong(), anyString(), any()))
          .thenReturn(gson.toJson(deliveryDocs));

      gdcInstructionService.serveInstructionRequest(
          gson.toJson(instructionReqWithWorkingState), httpHeaders);
    } catch (ReceivingException receivingException) {
      assertEquals(receivingException.getHttpStatus(), HttpStatus.BAD_REQUEST);
    }
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

      gdcInstructionService.serveInstructionRequest(
          gson.toJson(instructionRequest1), MockHttpHeaders.getHeaders("32612", "US"));
    } catch (ReceivingException e) {
      assertEquals(e.getErrorResponse().getErrorCode(), ReceivingException.GET_PTAG_ERROR_CODE);
      assertEquals(e.getErrorResponse().getErrorMessage(), ReceivingException.PTAG_NOT_FOUND);
    }
  }

  @Test
  public void testServeInstructionRequest_problemTag_not_Receivable() throws ReceivingException {
    try {
      TenantContext.setFacilityNum(32612);
      TenantContext.setFacilityCountryCode("US");
      HttpHeaders headers = MockHttpHeaders.getHeaders("32612", "US");
      ReflectionTestUtils.setField(gdcInstructionServiceSpied, "gson", gson);

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

      gdcInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), headers);
    } catch (ReceivingConflictException e) {
      assertEquals(e.getErrorCode(), "GLS-RCV-PRBLM-409");
      assertEquals(e.getDescription(), "Problem is not ready to receive.");
    }
  }
}
