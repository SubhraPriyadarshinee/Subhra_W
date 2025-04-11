package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CONTAINER_CREATE_TS;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DATE_FORMAT_ISO8601;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ELIGIBLE_TRANSFER_POS_CCM_CONFIG;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.InstructionError;
import com.walmart.move.nim.receiving.core.common.exception.InstructionErrorCode;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.message.publisher.JMSInstructionPublisher;
import com.walmart.move.nim.receiving.core.message.publisher.JMSReceiptPublisher;
import com.walmart.move.nim.receiving.core.message.publisher.MessagePublisher;
import com.walmart.move.nim.receiving.core.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.instructioncode.AccInstructionType;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.repositories.ReceiptRepository;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.CollectionUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ManualInstructionServiceTest extends ReceivingTestBase {

  @InjectMocks private ManualInstructionService manualInstructionService;
  @InjectMocks private InstructionHelperService instructionHelperService;
  @InjectMocks private JMSInstructionPublisher jmsInstructionPublisher;
  @InjectMocks private InstructionPersisterService instructionPersisterService;
  @InjectMocks private ContainerService containerService;
  @Mock private FdeServiceImpl fdeService;
  @Mock private DCFinService dcFinService;
  @Mock private ReceiptRepository receiptRepository;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private InstructionRepository instructionRepository;
  @Mock private ReceiptService receiptService;
  @Mock private DeliveryServiceRetryableImpl deliveryService;
  @Mock private JmsPublisher jmsPublisher;
  @Mock private AppConfig appConfig;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private DefaultLabelIdProcessor defaultLabelIdProcessor;
  @Mock private DeliveryDocumentHelper deliveryDocumentHelper;
  @Mock private DefaultSorterPublisher defaultSorterPublisher;
  @Mock private JMSReceiptPublisher JMSReceiptPublisher;
  @Mock private DefaultDeliveryDocumentsSearchHandler deliveryDocumentsSearchHandler;
  @Mock private ReceiptPublisher receiptPublisher;
  @Mock private PrintLabelHelper printLabelHelper;
  private Gson gson = new Gson();
  private final HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setCorrelationId("aafa2fcc-d299-4663-aa64-ba6f79704635");
    ReflectionTestUtils.setField(containerService, "configUtils", configUtils);
    ReflectionTestUtils.setField(instructionPersisterService, "containerService", containerService);

    ReflectionTestUtils.setField(instructionHelperService, "containerService", containerService);
    ReflectionTestUtils.setField(instructionHelperService, "jmsPublisher", jmsPublisher);
    ReflectionTestUtils.setField(instructionHelperService, "configUtils", configUtils);
    ReflectionTestUtils.setField(containerService, "gson", gson);
    ReflectionTestUtils.setField(
        manualInstructionService, "tenantSpecificConfigReader", configUtils);
    ReflectionTestUtils.setField(
        manualInstructionService, "instructionHelperService", instructionHelperService);
    ReflectionTestUtils.setField(
        manualInstructionService, "instructionPersisterService", instructionPersisterService);
  }

  @AfterMethod
  public void tearDown() {
    reset(containerPersisterService);
    reset(fdeService);
    reset(dcFinService);
    reset(receiptService);
    reset(deliveryService);
    reset(jmsPublisher);
    reset(appConfig);
    reset(instructionRepository);
    reset(deliveryDocumentHelper);
    reset(defaultSorterPublisher);
    reset(deliveryDocumentsSearchHandler);
  }

  @BeforeMethod()
  public void before() {
    DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_ISO8601);
    TenantContext.setAdditionalParams(CONTAINER_CREATE_TS, dateFormat.format(new Date()));
    when(configUtils.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.INSTRUCTION_PUBLISHER,
            MessagePublisher.class))
        .thenReturn(jmsInstructionPublisher);
    doReturn(Boolean.TRUE)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.ENABLE_FILTER_CANCELLED_PO);
    doReturn(deliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any());
  }

  private String getJSONFromPath(String path) throws IOException {
    String fileFromPath = new File(path).getCanonicalPath();
    return new String(Files.readAllBytes(Paths.get(fileFromPath)));
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "Ineligible for manual receiving. Mandatory fields are missing.")
  public void testCreateManualInstruction_ifMandatoryFieldsMissing() throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    instructionRequest
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setVendorPackCost(null);
    instructionRequest.setManualReceivingEnabled(Boolean.TRUE);
    manualInstructionService.createManualInstruction(instructionRequest, httpHeaders);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "The allowed number of cases for this item have been received. Please report the remaining as overage.")
  public void testCreateManualInstruction_ifReachedOverage() throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    instructionRequest.setManualReceivingEnabled(Boolean.TRUE);
    doReturn(15L).when(receiptService).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    manualInstructionService.createManualInstruction(instructionRequest, httpHeaders);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "Looks like the request couldn’t be completed right now. This could be caused by poor connectivity or system issues.")
  public void testCreateManualInstruction_ifFDECallFails() throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    instructionRequest.setManualReceivingEnabled(Boolean.TRUE);
    InstructionError instructionError =
        InstructionErrorCode.getErrorValue(ReceivingException.OF_GENERIC_ERROR);
    doThrow(
            new ReceivingException(
                instructionError.getErrorMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR,
                instructionError.getErrorCode(),
                instructionError.getErrorHeader()))
        .when(fdeService)
        .receive(any(), any());
    manualInstructionService.createManualInstruction(instructionRequest, httpHeaders);
  }

  @Test
  public void testCreateManualInstruction_doesNotHaveDeliveryDocInRequest()
      throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    doReturn(9999).when(appConfig).getMaxAllowedLabelsAtOnce();
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse1 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("4763030227", 1, 0L);
    List<ReceiptSummaryQtyByPoAndPoLineResponse> receiptSummaryQtyByPoAndPoLineResponses =
        new ArrayList<>();
    receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse1);
    doReturn(receiptSummaryQtyByPoAndPoLineResponses)
        .when(receiptService)
        .receivedQtyInVNPKByPoAndPoLineList(anyList(), anySet());
    InstructionResponse instructionResponse =
        manualInstructionService.getDeliveryDocumentWithOpenQtyForManualInstruction(
            instructionRequest, httpHeaders);
    assertNull(instructionResponse.getInstruction());
    assertFalse(instructionResponse.getDeliveryDocuments().isEmpty());
    assertEquals(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getOpenQty(),
        Integer.valueOf(15));
  }

  @Test
  public void testCreateManualInstruction_returnsResponseWithInstructionCode_when_isKotlin_enabled()
      throws ReceivingException {
    httpHeaders.add("isKotlin", "true");
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    doReturn(9999).when(appConfig).getMaxAllowedLabelsAtOnce();
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse1 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("4763030227", 1, 0L);
    List<ReceiptSummaryQtyByPoAndPoLineResponse> receiptSummaryQtyByPoAndPoLineResponses =
        new ArrayList<>();
    receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse1);
    doReturn(receiptSummaryQtyByPoAndPoLineResponses)
        .when(receiptService)
        .receivedQtyInVNPKByPoAndPoLineList(anyList(), anySet());
    InstructionResponse instructionResponse =
        manualInstructionService.getDeliveryDocumentWithOpenQtyForManualInstruction(
            instructionRequest, httpHeaders);
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        AccInstructionType.ACC_MANUAL_RCV_BUILD_PALLET.getInstructionCode());
    assertEquals(
        instructionResponse.getInstruction().getInstructionMsg(),
        AccInstructionType.ACC_MANUAL_RCV_BUILD_PALLET.getInstructionMsg());
    assertFalse(instructionResponse.getDeliveryDocuments().isEmpty());
    assertEquals(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getOpenQty(),
        Integer.valueOf(15));
  }

  @Test
  public void
      testCreateManualInstruction_returnsResponseWithInstructionCode_when_isKotlin_enabled_instruction_object_gtin_is_NUll()
          throws ReceivingException {
    httpHeaders.add("isKotlin", "true");
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    doReturn(9999).when(appConfig).getMaxAllowedLabelsAtOnce();
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse1 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("4763030227", 1, 0L);
    List<ReceiptSummaryQtyByPoAndPoLineResponse> receiptSummaryQtyByPoAndPoLineResponses =
        new ArrayList<>();
    receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse1);
    doReturn(receiptSummaryQtyByPoAndPoLineResponses)
        .when(receiptService)
        .receivedQtyInVNPKByPoAndPoLineList(anyList(), anySet());
    InstructionResponse instructionResponse =
        manualInstructionService.getDeliveryDocumentWithOpenQtyForManualInstruction(
            instructionRequest, httpHeaders);
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        AccInstructionType.ACC_MANUAL_RCV_BUILD_PALLET.getInstructionCode());
    assertEquals(instructionResponse.getInstruction().getGtin(), "00000943037204");
  }

  @Test
  public void testCreateManualInstruction_doesNotHaveDeliveryDocInRequestAndItemExistsInMultiPOPOL()
      throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    DeliveryDocument deliveryDoc = instructionRequest.getDeliveryDocuments().get(0);
    deliveryDoc.setPurchaseReferenceNumber("4763030228");
    deliveryDoc.getDeliveryDocumentLines().add(deliveryDoc.getDeliveryDocumentLines().get(0));
    instructionRequest.getDeliveryDocuments().add(deliveryDoc);
    doReturn(9999).when(appConfig).getMaxAllowedLabelsAtOnce();
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse1 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("4763030227", 1, 0L);
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse2 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("4763030228", 1, 0L);
    List<ReceiptSummaryQtyByPoAndPoLineResponse> receiptSummaryQtyByPoAndPoLineResponses =
        new ArrayList<>();
    receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse1);
    receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse2);
    doReturn(receiptSummaryQtyByPoAndPoLineResponses)
        .when(receiptService)
        .receivedQtyInVNPKByPoAndPoLineList(anyList(), anySet());
    InstructionResponse instructionResponse =
        manualInstructionService.getDeliveryDocumentWithOpenQtyForManualInstruction(
            instructionRequest, httpHeaders);
    assertNull(instructionResponse.getInstruction());
    assertEquals(instructionResponse.getDeliveryDocuments().size(), 2);
    assertEquals(
        instructionResponse.getDeliveryDocuments().get(1).getDeliveryDocumentLines().size(), 2);
    for (DeliveryDocument deliveryDocument : instructionResponse.getDeliveryDocuments()) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        assertEquals(deliveryDocumentLine.getOpenQty(), Integer.valueOf(15));
      }
    }
  }

  @Test
  public void
      testCreateManualInstruction_doesNotHaveDeliveryDocInRequestAndItemExistsInMultiPOPOL_AutoSelect()
          throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    DeliveryDocument deliveryDoc = instructionRequest.getDeliveryDocuments().get(0);
    deliveryDoc.getDeliveryDocumentLines().add(deliveryDoc.getDeliveryDocumentLines().get(0));
    instructionRequest.getDeliveryDocuments().add(deliveryDoc);
    doReturn(Boolean.TRUE).when(appConfig).isManualPoLineAutoSelectionEnabled();
    doReturn(9999).when(appConfig).getMaxAllowedLabelsAtOnce();
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse1 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("4763030227", 1, 0L);
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse2 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("4763030227", 2, 0L);
    List<ReceiptSummaryQtyByPoAndPoLineResponse> receiptSummaryQtyByPoAndPoLineResponses =
        new ArrayList<>();
    receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse1);
    receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse2);
    doReturn(receiptSummaryQtyByPoAndPoLineResponses)
        .when(receiptService)
        .receivedQtyInVNPKByPoAndPoLineList(anyList(), anySet());
    InstructionResponse instructionResponse =
        manualInstructionService.getDeliveryDocumentWithOpenQtyForManualInstruction(
            instructionRequest, httpHeaders);
    assertNull(instructionResponse.getInstruction());
    assertEquals(instructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        instructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(), 1);
    assertEquals(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getOpenQty(),
        Integer.valueOf(60));
    assertEquals(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        1);
  }

  @Test
  public void testCreateManualInstruction_doesNotHaveDeliveryDocInRequestAndItemExistsInMultiPOL()
      throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    DeliveryDocument deliveryDoc = instructionRequest.getDeliveryDocuments().get(0);
    deliveryDoc.setPurchaseReferenceNumber("4763030228");

    DeliveryDocumentLine documentLine =
        MockInstruction.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    documentLine.setPurchaseReferenceLineNumber(2);
    documentLine.setTotalOrderQty(20);
    deliveryDoc.getDeliveryDocumentLines().add(documentLine);

    doReturn(9999).when(appConfig).getMaxAllowedLabelsAtOnce();
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse1 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("4763030228", 1, 0L);
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse2 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("4763030228", 2, 0L);
    List<ReceiptSummaryQtyByPoAndPoLineResponse> receiptSummaryQtyByPoAndPoLineResponses =
        new ArrayList<>();
    receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse1);
    receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse2);
    doReturn(receiptSummaryQtyByPoAndPoLineResponses)
        .when(receiptService)
        .receivedQtyInVNPKByPoAndPoLineList(anyList(), anySet());
    InstructionResponse instructionResponse =
        manualInstructionService.getDeliveryDocumentWithOpenQtyForManualInstruction(
            instructionRequest, httpHeaders);
    assertNull(instructionResponse.getInstruction());
    assertEquals(instructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        instructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(), 2);
    List<Integer> expectedOpenQty = new ArrayList<>();
    expectedOpenQty.add(15);
    expectedOpenQty.add(25);
    List<Integer> openQty = new ArrayList<>();
    openQty.add(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getOpenQty());
    openQty.add(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(1)
            .getOpenQty());
    assertTrue(openQty.containsAll(expectedOpenQty));
  }

  @Test
  public void testCreateManualInstruction_success() throws ReceivingException, IOException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    instructionRequest.setManualReceivingEnabled(Boolean.TRUE);
    doReturn(Boolean.FALSE).when(configUtils).isFeatureFlagEnabled(anyString());
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(
            getJSONFromPath(
                "../receiving-test/src/main/resources/json/manual_instruction_response.json"));
    when(instructionPersisterService.saveInstruction(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(receiptRepository.saveAll(anyList())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    when(configUtils.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.SORTER_PUBLISHER,
            SorterPublisher.class))
        .thenReturn(defaultSorterPublisher);
    when(configUtils.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.RECEIPT_EVENT_HANDLER,
            MessagePublisher.class))
        .thenReturn(JMSReceiptPublisher);
    when(configUtils.getCcmValue(
            getFacilityNum(), ELIGIBLE_TRANSFER_POS_CCM_CONFIG, DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE))
        .thenReturn(DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE);

    InstructionResponse instructionResponse =
        manualInstructionService.createManualInstruction(instructionRequest, httpHeaders);
    assertNotNull(instructionResponse.getInstruction());

    ArgumentCaptor<FdeCreateContainerRequest> fdeCaptor =
        ArgumentCaptor.forClass(FdeCreateContainerRequest.class);
    verify(fdeService, times(1)).receive(fdeCaptor.capture(), eq(httpHeaders));
    assertEquals(
        JacksonParser.writeValueAsStringExcludeNull(fdeCaptor.getValue()).replaceAll("\\s+", ""),
        getJSONFromPath(
                "../receiving-test/src/main/resources/json/fde_manual_instruction_request.json")
            .replaceAll("\\s+", ""));

    ArgumentCaptor<ReceivingJMSEvent> wfmPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(3))
        .publish(
            eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC),
            wfmPublishCaptor.capture(),
            eq(Boolean.FALSE));
    List<ReceivingJMSEvent> receivingJMSEventList = wfmPublishCaptor.getAllValues();
    String WFMUpdateMessage = receivingJMSEventList.get(1).getMessageBody();
    String WFMCompleteMessage = receivingJMSEventList.get(2).getMessageBody();

    assertTrue(
        validateContract(
            getJSONFromPath(
                "../receiving-test/src/main/resources/jsonSchema/manualInstructionUpdateMessageSchema.json"),
            WFMUpdateMessage));
    assertTrue(WFMUpdateMessage.contains("\"userId\":\"sysadmin\""));
    assertTrue(WFMUpdateMessage.contains("\"activityName\":\"ACL\""));

    assertTrue(
        validateContract(
            getJSONFromPath(
                ("../receiving-test/src/main/resources/jsonSchema/aclWFMCompleteMessageSchema.json")),
            WFMCompleteMessage));
    ArgumentCaptor<Container> containerCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerCaptor.capture());

    Container savedContainer = containerCaptor.getValue();
    assertNotNull(savedContainer.getCompleteTs());
    assertNull(savedContainer.getParentTrackingId());
    assertEquals(savedContainer.getContainerItems().size(), 1);
    assertEquals(savedContainer.getCreateUser(), "sysadmin");
    assertTrue(CollectionUtils.isEmpty(savedContainer.getChildContainers()));
    assertTrue(savedContainer.getIsConveyable());
    assertTrue(savedContainer.getOnConveyor());

    ArgumentCaptor<Instruction> instructionCaptor = ArgumentCaptor.forClass(Instruction.class);
    verify(instructionRepository, times(2)).save(instructionCaptor.capture());
    Instruction savedInstruction = instructionCaptor.getValue();
    assertEquals(savedInstruction.getActivityName(), "ACL");

    verify(receiptService, times(1))
        .createReceiptsFromInstruction(
            any(UpdateInstructionRequest.class), eq(null), eq("sysadmin"));
    ArgumentCaptor<ContainerDTO> containerPublishCaptor =
        ArgumentCaptor.forClass(ContainerDTO.class);
    verify(JMSReceiptPublisher, times(1)).publish(containerPublishCaptor.capture(), any());
    String publishedContainer = gson.toJson(containerPublishCaptor.getValue());
    assertTrue(
        validateContract(
            getJSONFromPath(
                "../receiving-test/src/main/resources/jsonSchema/aclReceivingContainerMessageSchema.json"),
            publishedContainer));
  }

  @Test
  public void testCreateManualInstruction_openQtyIsMoreThanAllowedReceivableQty()
      throws ReceivingException {
    if (httpHeaders.containsKey("isKotlin")) httpHeaders.remove("isKotlin");
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse1 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("4763030227", 1, 0L);
    List<ReceiptSummaryQtyByPoAndPoLineResponse> receiptSummaryQtyByPoAndPoLineResponses =
        new ArrayList<>();
    receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse1);
    doReturn(receiptSummaryQtyByPoAndPoLineResponses)
        .when(receiptService)
        .receivedQtyInVNPKByPoAndPoLineList(anyList(), anySet());
    doReturn(5).when(appConfig).getMaxAllowedLabelsAtOnce();
    InstructionResponse instructionResponse =
        manualInstructionService.getDeliveryDocumentWithOpenQtyForManualInstruction(
            instructionRequest, httpHeaders);
    assertNull(instructionResponse.getInstruction());
    assertFalse(instructionResponse.getDeliveryDocuments().isEmpty());
    assertEquals(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getOpenQty(),
        Integer.valueOf(5));
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "The allowed number of cases for this item have been received. Please report the remaining as overage.")
  public void testCreateManualInstruction_openQtyIsLessThanZero() throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    doReturn(5).when(appConfig).getMaxAllowedLabelsAtOnce();
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse1 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("4763030227", 1, 15L);
    List<ReceiptSummaryQtyByPoAndPoLineResponse> receiptSummaryQtyByPoAndPoLineResponses =
        new ArrayList<>();
    receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse1);
    doReturn(receiptSummaryQtyByPoAndPoLineResponses)
        .when(receiptService)
        .receivedQtyInVNPKByPoAndPoLineList(anyList(), anySet());
    InstructionResponse instructionResponse =
        manualInstructionService.getDeliveryDocumentWithOpenQtyForManualInstruction(
            instructionRequest, httpHeaders);
    fail("Exception should have been thrown");
  }

  @Test
  public void testCreateManualInstruction_openQtyIsLessThanZeroForAllPoLines_AutoSelect() {
    try {
      InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
      DeliveryDocument deliveryDoc = instructionRequest.getDeliveryDocuments().get(0);
      DeliveryDocumentLine docLine = deliveryDoc.getDeliveryDocumentLines().get(0);
      deliveryDoc.getDeliveryDocumentLines().add(docLine);
      docLine.setPurchaseReferenceLineNumber(2);
      deliveryDoc.getDeliveryDocumentLines().add(docLine);
      instructionRequest.getDeliveryDocuments().add(deliveryDoc);
      ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse1 =
          new ReceiptSummaryQtyByPoAndPoLineResponse("4763030227", 1, 15L);
      ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse2 =
          new ReceiptSummaryQtyByPoAndPoLineResponse("4763030227", 2, 15L);
      List<ReceiptSummaryQtyByPoAndPoLineResponse> receiptSummaryQtyByPoAndPoLineResponses =
          new ArrayList<>();
      receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse1);
      receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse2);
      doReturn(receiptSummaryQtyByPoAndPoLineResponses)
          .when(receiptService)
          .receivedQtyInVNPKByPoAndPoLineList(anyList(), anySet());
      doReturn(Boolean.TRUE).when(appConfig).isManualPoLineAutoSelectionEnabled();
      doReturn(5).when(appConfig).getMaxAllowedLabelsAtOnce();
      InstructionResponse instructionResponse =
          manualInstructionService.getDeliveryDocumentWithOpenQtyForManualInstruction(
              instructionRequest, httpHeaders);
      fail("Exception should have been thrown");
    } catch (ReceivingException e) {
      RangeErrorResponse rangeErrorResponse = (RangeErrorResponse) e.getErrorResponse();
      DeliveryDocument overageDeliveryDocument = (rangeErrorResponse).getDeliveryDocument();
      assertEquals(overageDeliveryDocument.getDeliveryDocumentLines().size(), 1);
      assertEquals(
          overageDeliveryDocument
              .getDeliveryDocumentLines()
              .get(0)
              .getPurchaseReferenceLineNumber(),
          2);
      assertEquals(rangeErrorResponse.getRcvdqtytilldate(), 15);
    }
  }

  @Test
  public void
      testCreateManualInstruction_openQtyIsLessThanZeroForAllPoLines_AutoSelect_isKotLineTrue() {
    try {
      httpHeaders.add("isKotlin", "true");
      InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
      DeliveryDocument deliveryDoc = instructionRequest.getDeliveryDocuments().get(0);
      DeliveryDocumentLine docLine = deliveryDoc.getDeliveryDocumentLines().get(0);
      deliveryDoc.getDeliveryDocumentLines().add(docLine);
      docLine.setPurchaseReferenceLineNumber(2);
      deliveryDoc.getDeliveryDocumentLines().add(docLine);
      instructionRequest.getDeliveryDocuments().add(deliveryDoc);
      ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse1 =
          new ReceiptSummaryQtyByPoAndPoLineResponse("4763030227", 1, 15L);
      ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse2 =
          new ReceiptSummaryQtyByPoAndPoLineResponse("4763030227", 2, 15L);
      List<ReceiptSummaryQtyByPoAndPoLineResponse> receiptSummaryQtyByPoAndPoLineResponses =
          new ArrayList<>();
      receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse1);
      receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse2);
      doReturn(receiptSummaryQtyByPoAndPoLineResponses)
          .when(receiptService)
          .receivedQtyInVNPKByPoAndPoLineList(anyList(), anySet());
      doReturn(Boolean.TRUE).when(appConfig).isManualPoLineAutoSelectionEnabled();
      doReturn(5).when(appConfig).getMaxAllowedLabelsAtOnce();
      InstructionResponse instructionResponse =
          manualInstructionService.getDeliveryDocumentWithOpenQtyForManualInstruction(
              instructionRequest, httpHeaders);
      assertEquals(instructionResponse.getInstruction() != null, true);
    } catch (ReceivingException e) {
      RangeErrorResponse rangeErrorResponse = (RangeErrorResponse) e.getErrorResponse();
      DeliveryDocument overageDeliveryDocument = (rangeErrorResponse).getDeliveryDocument();
      assertEquals(overageDeliveryDocument.getDeliveryDocumentLines().size(), 1);
      assertEquals(
          overageDeliveryDocument
              .getDeliveryDocumentLines()
              .get(0)
              .getPurchaseReferenceLineNumber(),
          2);
      assertEquals(rangeErrorResponse.getRcvdqtytilldate(), 15);
    }
  }

  @Test
  public void testAutoSelectPoLineForManualReceiving_MultiPo() throws ReceivingException {
    DeliveryDocument deliveryDocument = MockInstruction.getDeliveryDocuments().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    doReturn(MockInstruction.getMultiPoDeliveryDocuments())
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocumentByUpc(anyLong(), anyString(), any(HttpHeaders.class));

    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse1 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("4763030227", 1, 0L);

    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse2 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("4763030228", 1, 0L);

    List<ReceiptSummaryQtyByPoAndPoLineResponse> receiptSummaryQtyByPoAndPoLineResponses =
        new ArrayList<>();

    receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse1);
    receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse2);
    doReturn(receiptSummaryQtyByPoAndPoLineResponses)
        .when(receiptService)
        .receivedQtyInVNPKByPoAndPoLineList(anyList(), anySet());
    DeliveryDocument autoSelectedDocument =
        manualInstructionService.autoSelectPoLineForManualReceiving(
            deliveryDocument, deliveryDocumentLine, httpHeaders);

    assertEquals(autoSelectedDocument.getPurchaseReferenceNumber(), "4763030227");

    assertEquals(
        autoSelectedDocument.getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber(), 1);

    assertEquals(
        autoSelectedDocument.getDeliveryDocumentLines().get(0).getOpenQty(), Integer.valueOf(10));
  }

  @Test
  public void testAutoSelectPoLineForManualReceiving_MultiPoLine() throws ReceivingException {
    DeliveryDocument deliveryDocument = MockInstruction.getDeliveryDocuments().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    doReturn(MockInstruction.getMultiPoLineDeliveryDocuments())
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocumentByUpc(anyLong(), anyString(), any(HttpHeaders.class));
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse1 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("4763030227", 1, 0L);
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse2 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("4763030227", 2, 0L);
    List<ReceiptSummaryQtyByPoAndPoLineResponse> receiptSummaryQtyByPoAndPoLineResponses =
        new ArrayList<>();
    receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse1);
    receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse2);
    doReturn(receiptSummaryQtyByPoAndPoLineResponses)
        .when(receiptService)
        .receivedQtyInVNPKByPoAndPoLineList(anyList(), anySet());
    DeliveryDocument autoSelectedDocument =
        manualInstructionService.autoSelectPoLineForManualReceiving(
            deliveryDocument, deliveryDocumentLine, httpHeaders);
    assertEquals(autoSelectedDocument.getPurchaseReferenceNumber(), "4763030227");
    assertEquals(
        autoSelectedDocument.getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber(), 1);
    assertEquals(
        autoSelectedDocument.getDeliveryDocumentLines().get(0).getOpenQty(), Integer.valueOf(10));
  }

  @Test
  public void testAutoSelectPoLineForManualReceiving_NoOpenQty() {
    try {
      DeliveryDocument deliveryDocument = MockInstruction.getDeliveryDocuments().get(0);
      DeliveryDocumentLine deliveryDocumentLine =
          deliveryDocument.getDeliveryDocumentLines().get(0);
      doReturn(MockInstruction.getMultiPoLineDeliveryDocuments())
          .when(deliveryDocumentsSearchHandler)
          .fetchDeliveryDocumentByUpc(anyLong(), anyString(), any(HttpHeaders.class));
      doReturn(true)
          .when(configUtils)
          .isFeatureFlagEnabled(ReceivingConstants.IS_RECEIPT_AGGREGATOR_CHECK_ENABLED);
      ReceiptSummaryEachesResponse receiptSummaryQtyByPoAndPoLineResponse1 =
          new ReceiptSummaryEachesResponse("4763030227", 1, null, 60L);
      ReceiptSummaryEachesResponse receiptSummaryQtyByPoAndPoLineResponse2 =
          new ReceiptSummaryEachesResponse("4763030227", 2, null, 60L);
      List<ReceiptSummaryEachesResponse> receiptSummaryQtyByPoAndPoLineResponses =
          new ArrayList<>();
      receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse1);
      receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse2);
      doReturn(receiptSummaryQtyByPoAndPoLineResponses)
          .when(receiptService)
          .receivedQtyByPoAndPoLineList(anyList(), anySet());
      DeliveryDocument autoSelectedDocument =
          manualInstructionService.autoSelectPoLineForManualReceiving(
              deliveryDocument, deliveryDocumentLine, httpHeaders);
      fail("Exception should have been thrown");
    } catch (ReceivingException e) {
      RangeErrorResponse rangeErrorResponse = (RangeErrorResponse) e.getErrorResponse();
      DeliveryDocument overageDeliveryDocument = (rangeErrorResponse).getDeliveryDocument();
      assertEquals(overageDeliveryDocument.getDeliveryDocumentLines().size(), 1);
      DeliveryDocument mockDeliveryDocument = MockInstruction.getDeliveryDocuments().get(0);
      mockDeliveryDocument.getDeliveryDocumentLines().get(0).setOpenQty(-5);
      mockDeliveryDocument.getDeliveryDocumentLines().get(0).setTotalReceivedQty(15);
      assertEquals(overageDeliveryDocument.toString(), mockDeliveryDocument.toString());
      assertEquals(rangeErrorResponse.getRcvdqtytilldate(), 15);
    }
  }
}
