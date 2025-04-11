package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.validators.InstructionStateValidator;
import com.walmart.move.nim.receiving.core.common.validators.PurchaseReferenceValidator;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.PrintJob;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.message.publisher.JMSInstructionPublisher;
import com.walmart.move.nim.receiving.core.message.publisher.MessagePublisher;
import com.walmart.move.nim.receiving.core.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.core.mock.data.MockUpdateInstructionRequest;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.repositories.ReceiptCustomRepository;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class UpdateInstructionServiceTest extends ReceivingTestBase {

  @InjectMocks @Spy private InstructionService instructionService;
  @InjectMocks private InstructionPersisterService instructionPersisterService;
  @InjectMocks private DefaultUpdateInstructionHandler defaultUpdateInstructionHandler;
  @InjectMocks private InstructionHelperService instructionHelperService;
  @InjectMocks private JMSInstructionPublisher jmsInstructionPublisher;

  @Mock private InstructionRepository instructionRepository;

  @Mock private ReceiptCustomRepository receiptCustomRepository;

  @Mock private ReceiptService receiptService;

  @Mock private ContainerService containerService;

  @Mock private JmsPublisher jmsPublisher;

  @Mock private PrintJobService printJobService;
  @Mock private AppConfig appConfig;
  @Mock private TenantSpecificConfigReader configUtils;

  @Mock private DeliveryServiceImpl deliveryService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private PurchaseReferenceValidator purchaseReferenceValidator;

  private Gson gson = new Gson();
  private List<Receipt> receipts = new ArrayList<>();
  private Instruction completedInstruction;
  private UpdateInstructionRequest updateInstructionRequest1;
  private UpdateInstructionRequest updateInstructionRequest2;
  private UpdateInstructionRequest updateInstructionRequest3;
  private UpdateInstructionRequest updateInstructionRequest4;
  private UpdateInstructionRequest updateInstructionRequest5;
  private UpdateInstructionRequest updateInstructionRequest6;
  private Long instructionId = Long.valueOf("2323");
  private PrintJob printJob = MockInstruction.getPrintJob();
  private HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
  private Instruction pendingInstruction = MockInstruction.getPendingInstruction();
  private JsonObject defaultFeatureFlagsByFacility = new JsonObject();

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");

    ReflectionTestUtils.setField(
        instructionService, "instructionPersisterService", instructionPersisterService);
    ReflectionTestUtils.setField(
        instructionService, "purchaseReferenceValidator", purchaseReferenceValidator);
    ReflectionTestUtils.setField(
        defaultUpdateInstructionHandler,
        "instructionPersisterService",
        instructionPersisterService);
    ReflectionTestUtils.setField(
        defaultUpdateInstructionHandler, "instructionHelperService", instructionHelperService);
    ReflectionTestUtils.setField(
        defaultUpdateInstructionHandler,
        "instructionStateValidator",
        new InstructionStateValidator());

    updateInstructionRequest1 = new UpdateInstructionRequest();
    DocumentLine documentLine1 = new DocumentLine();
    documentLine1.setQuantity(2);
    documentLine1.setPurchaseRefType("CROSSU");
    List<DocumentLine> documentLines1 = new ArrayList<>();
    documentLines1.add(documentLine1);
    updateInstructionRequest1.setDeliveryDocumentLines(documentLines1);

    updateInstructionRequest2 = new UpdateInstructionRequest();
    DocumentLine documentLine2 = new DocumentLine();
    documentLine2.setQuantity(3);
    documentLine2.setExpectedQty(1l);
    documentLine2.setMaxReceiveQty(1l);
    documentLine2.setPurchaseReferenceNumber("2324341341231");
    documentLine2.setPurchaseReferenceLineNumber(2);
    documentLine2.setPurchaseRefType("CROSSU");
    documentLine2.setMaxOverageAcceptQty(3l);
    List<DocumentLine> documentLines2 = new ArrayList<>();
    documentLines2.add(documentLine2);
    updateInstructionRequest2.setDeliveryDocumentLines(documentLines2);

    updateInstructionRequest3 = new UpdateInstructionRequest();
    DocumentLine documentLine3 = new DocumentLine();
    documentLine3.setQuantity(2);
    documentLine3.setMaxOverageAcceptQty(null);
    documentLine3.setExpectedQty(1l);
    documentLine3.setMaxReceiveQty(1l);
    documentLine3.setPurchaseReferenceNumber("2324341341231");
    documentLine3.setPurchaseReferenceLineNumber(2);
    documentLine3.setPurchaseRefType("CROSSU");
    List<DocumentLine> documentLines3 = new ArrayList<>();
    documentLines3.add(documentLine3);
    updateInstructionRequest3.setDeliveryDocumentLines(documentLines3);

    updateInstructionRequest4 = new UpdateInstructionRequest();
    DocumentLine documentLine4 = new DocumentLine();
    documentLine4.setQuantity(2);
    documentLine4.setMaxOverageAcceptQty(1l);
    documentLine4.setExpectedQty(1l);
    documentLine4.setMaxReceiveQty(1l);
    documentLine4.setPurchaseReferenceNumber("2324341341231");
    documentLine4.setPurchaseReferenceLineNumber(2);
    List<DocumentLine> documentLines4 = new ArrayList<>();
    documentLines4.add(documentLine4);
    updateInstructionRequest4.setDeliveryDocumentLines(documentLines4);

    updateInstructionRequest5 = new UpdateInstructionRequest();
    DocumentLine documentLine5 = new DocumentLine();
    documentLine5.setQuantity(2);
    documentLine5.setMaxOverageAcceptQty(20l);
    documentLine5.setExpectedQty(1l);
    documentLine5.setMaxReceiveQty(1l);
    documentLine5.setPurchaseReferenceNumber("2324341341231");
    documentLine5.setPurchaseReferenceLineNumber(2);
    documentLine5.setPurchaseRefType("CROSSU");
    List<DocumentLine> documentLines5 = new ArrayList<>();
    documentLines5.add(documentLine5);
    updateInstructionRequest5.setDeliveryDocumentLines(documentLines5);

    updateInstructionRequest6 = new UpdateInstructionRequest();
    DocumentLine documentLine6 = new DocumentLine();
    documentLine6.setQuantity(2);
    documentLine6.setMaxOverageAcceptQty(10l);
    documentLine6.setExpectedQty(1l);
    documentLine6.setMaxReceiveQty(1l);
    documentLine6.setPurchaseReferenceNumber("2324341341231");
    documentLine6.setPurchaseReferenceLineNumber(2);
    documentLine6.setPurchaseRefType("CROSSU");
    List<DocumentLine> documentLines6 = new ArrayList<>();
    documentLines6.add(documentLine6);
    updateInstructionRequest6.setDeliveryDocumentLines(documentLines6);

    Receipt receipt = new Receipt();
    receipt.setProblemId(null);
    receipt.setCreateUserId("sysadmin");
    receipt.setEachQty(4);
    receipt.setDeliveryNumber(21231313l);
    receipt.setDoorNumber("101");
    receipt.setPurchaseReferenceLineNumber(1);
    receipt.setPurchaseReferenceNumber("9763140004");
    receipt.setQuantity(4);
    receipt.setQuantityUom("ZA");
    receipts.add(receipt);
  }

  @BeforeClass
  public void setReflectionTestUtil() {
    ReflectionTestUtils.setField(instructionService, "gson", gson);
  }

  @BeforeMethod
  public void setUpTestDataBeforeEachTest() throws ReceivingException {
    completedInstruction = MockInstruction.getCompleteInstruction();
    when(configUtils.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.INSTRUCTION_PUBLISHER,
            MessagePublisher.class))
        .thenReturn(jmsInstructionPublisher);
  }

  @Test
  public void testUpdateInstructionWhenInstructionIsAlreadyCompleted() throws ReceivingException {

    doReturn(defaultUpdateInstructionHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(completedInstruction));
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);

    try {
      instructionService.updateInstruction(
          instructionId, updateInstructionRequest6, "", httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getHttpStatus());
      assertEquals(
          ReceivingException.ERROR_HEADER_PALLET_COMPLETED, e.getErrorResponse().getErrorHeader());
      assertEquals(
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_CODE_FOR_ALREADY_COMPLETED,
          e.getErrorResponse().getErrorCode());
      assertEquals(
          String.format(
              ReceivingException.COMPLETE_INSTRUCTION_ALREADY_COMPLETE,
              completedInstruction.getCompleteUserId()),
          e.getErrorResponse().getErrorMessage());
    }
    reset(instructionRepository);
  }

  @Test
  public void testUpdateInstructionWhenInstructionIsAlreadyCancelled() throws ReceivingException {
    completedInstruction.setReceivedQuantity(0);
    doReturn(defaultUpdateInstructionHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(completedInstruction));
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    try {
      instructionService.updateInstruction(
          instructionId, updateInstructionRequest6, "", httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getHttpStatus());
      assertEquals(
          ReceivingException.ERROR_HEADER_PALLET_CANCELLED, e.getErrorResponse().getErrorHeader());
      assertEquals(
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_CODE_FOR_ALREADY_COMPLETED,
          e.getErrorResponse().getErrorCode());
      assertEquals(
          String.format(
              ReceivingException.COMPLETE_INSTRUCTION_PALLET_CANCELLED,
              completedInstruction.getCompleteUserId()),
          e.getErrorResponse().getErrorMessage());
    }
    reset(instructionRepository);
  }

  @Test
  public void testUpdateInstructionWhenExceedsQuantityNeeded() throws ReceivingException {
    pendingInstruction.setReceivedQuantity(0);
    pendingInstruction.setProjectedReceiveQty(1);
    doReturn(defaultUpdateInstructionHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(pendingInstruction));
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    try {
      instructionService.updateInstruction(
          instructionId, updateInstructionRequest1, "", httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(e.getMessage(), ReceivingException.UPDATE_INSTRUCTION_EXCEEDS_QUANTITY);
      assertEquals(e.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
    reset(instructionRepository);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          ReceivingException.UPDATE_INSTRUCTION_REACHED_MAXIMUM_THRESHOLD)
  public void testUpdateInstructionWhenInstructionReachedMaxThreshold() throws ReceivingException {
    pendingInstruction.setReceivedQuantity(4);
    pendingInstruction.setProjectedReceiveQty(10);
    doReturn(defaultUpdateInstructionHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(pendingInstruction));

    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(Long.valueOf("4"));
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);

    instructionService.updateInstruction(instructionId, updateInstructionRequest2, "", httpHeaders);
    assertEquals(pendingInstruction.getCompleteTs(), null);
    assertEquals(pendingInstruction.getCompleteUserId(), null);
    // Reset the pending instruction. So, that it can be used in other tests
    reset(receiptService);
    reset(instructionRepository);
  }

  @Test
  public void testUpdateInstructionWhenInstructionNearOverageLimit() throws ReceivingException {
    pendingInstruction.setReceivedQuantity(1);
    pendingInstruction.setProjectedReceiveQty(3);
    doReturn(defaultUpdateInstructionHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(pendingInstruction));

    when(receiptCustomRepository.receivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(new ReceiptSummaryQtyByPoAndPoLineResponse("2324341341231", 2, 1l));
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    try {
      instructionService.updateInstruction(
          instructionId, updateInstructionRequest3, "", httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(
          e.getMessage(),
          String.format(ReceivingException.UPDATE_INSTRUCTION_NEAR_OVERAGE_LIMIT, "1"));
      assertEquals(e.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
    reset(receiptCustomRepository);
    reset(instructionRepository);
  }

  @Test
  public void
      testUpdateInstructionWhenCurrentReceivedQuantityIsMoreThanTheExpectedQuantityToBeReceived()
          throws ReceivingException {

    pendingInstruction.setReceivedQuantity(1);
    pendingInstruction.setProjectedReceiveQty(3);
    doReturn(defaultUpdateInstructionHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(pendingInstruction));

    when(receiptCustomRepository.receivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(new ReceiptSummaryQtyByPoAndPoLineResponse("2324341341231", 2, 1l));
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    try {
      instructionService.updateInstruction(
          instructionId, updateInstructionRequest5, "", httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(
          e.getMessage(),
          String.format(ReceivingException.UPDATE_INSTRUCTION_NEAR_OVERAGE_LIMIT, "1"));
      assertEquals(e.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
    reset(receiptCustomRepository);
    reset(instructionRepository);
  }

  @Test
  public void testUpdateInstructionWhenSuccessfulUpdateWithoutCaseLabels()
      throws ReceivingException {
    reset(jmsPublisher);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    pendingInstruction.setReceivedQuantity(0);
    pendingInstruction.setProjectedReceiveQty(3);
    pendingInstruction.setPrintChildContainerLabels(null);
    doReturn(defaultUpdateInstructionHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(pendingInstruction));

    when(receiptCustomRepository.receivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(new ReceiptSummaryQtyByPoAndPoLineResponse("2324341341231", 2, 0l));

    List<Receipt> receipts = new ArrayList<>();

    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(this.printJob);

    when(receiptService.createReceiptsFromInstruction(
            any(UpdateInstructionRequest.class), anyString(), anyString()))
        .thenReturn(receipts);

    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    InstructionResponse updateInstructionResponse =
        instructionService.updateInstruction(
            instructionId, updateInstructionRequest5, "", httpHeaders);

    assertEquals(updateInstructionResponse.getInstruction().getReceivedQuantity(), 0);
    verify(jmsPublisher, times(1))
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    reset(receiptService);
    reset(instructionRepository);
    reset(printJobService);
  }

  @Test
  public void testOldPrintUpdateInstructionWhenSuccessfulUpdateWithoutCaseLabels()
      throws ReceivingException {
    reset(jmsPublisher);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    pendingInstruction.setReceivedQuantity(0);
    pendingInstruction.setProjectedReceiveQty(3);
    pendingInstruction.setPrintChildContainerLabels(null);
    doReturn(defaultUpdateInstructionHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(pendingInstruction));

    when(receiptCustomRepository.receivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(new ReceiptSummaryQtyByPoAndPoLineResponse("2324341341231", 2, 0l));

    List<Receipt> receipts = new ArrayList<>();

    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(this.printJob);

    when(receiptService.createReceiptsFromInstruction(
            any(UpdateInstructionRequest.class), anyString(), anyString()))
        .thenReturn(receipts);

    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(false);
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    InstructionResponseImplOld updateInstructionResponse =
        (InstructionResponseImplOld)
            instructionService.updateInstruction(
                instructionId, updateInstructionRequest5, "", httpHeaders);

    assertEquals(updateInstructionResponse.getInstruction().getReceivedQuantity(), 0);
    assertEquals(updateInstructionResponse.getPrintJob(), null);
    verify(jmsPublisher, times(1))
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    reset(receiptService);
    reset(instructionRepository);
    reset(printJobService);
  }

  @Test
  public void testUpdateInstructionWhenSuccessfulUpdateWithCaseLabels() throws ReceivingException {
    reset(jmsPublisher);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    pendingInstruction.setProblemTagId("123");
    pendingInstruction.setReceivedQuantity(0);
    pendingInstruction.setProjectedReceiveQty(3);
    pendingInstruction.setPrintChildContainerLabels(true);
    List<ContainerDetails> childContainers = new ArrayList<>();
    ContainerDetails containerDetails = new ContainerDetails();
    childContainers.add(containerDetails);
    pendingInstruction.setChildContainers(childContainers);

    Labels labels = new Labels();
    List<String> availableLabels = new ArrayList<>();
    availableLabels.add("1233321");
    availableLabels.add("343222");
    labels.setAvailableLabels(availableLabels);
    labels.setUsedLabels(new ArrayList<>());
    pendingInstruction.setLabels(labels);

    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(pendingInstruction));
    doReturn(defaultUpdateInstructionHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    when(receiptCustomRepository.receivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(new ReceiptSummaryQtyByPoAndPoLineResponse("2324341341231", 2, 0l));
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    List<String> trackingIds = new ArrayList<>();
    trackingIds.add("1233321");
    trackingIds.add("343222");

    when(containerService.getCreatedChildContainerTrackingIds(any(), anyInt(), anyInt()))
        .thenReturn(trackingIds);

    Map<String, Object> printJob = new HashMap<>();
    printJob.put(ReceivingConstants.PRINT_CLIENT_ID_KEY, "OF");

    when(containerService.getCreatedChildContainerLabels(any(), anyInt(), anyInt()))
        .thenReturn(printJob);

    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(this.printJob);

    when(receiptService.createReceiptsFromInstruction(
            any(UpdateInstructionRequest.class), anyString(), anyString()))
        .thenReturn(receipts);

    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);

    InstructionResponseImplNew updateInstructionResponse =
        (InstructionResponseImplNew)
            instructionService.updateInstruction(
                instructionId, updateInstructionRequest6, "", httpHeaders);

    assertEquals(updateInstructionResponse.getInstruction().getLabels().getUsedLabels().size(), 2);
    assertEquals(
        updateInstructionResponse.getPrintJob().get(ReceivingConstants.PRINT_CLIENT_ID_KEY), "OF");
    verify(jmsPublisher, times(1))
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    reset(receiptService);
    reset(instructionRepository);
    reset(printJobService);
  }

  @Test
  public void testOldPrintUpdateInstructionWhenSuccessfulUpdateWithCaseLabels()
      throws ReceivingException {
    reset(jmsPublisher);

    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    pendingInstruction.setProblemTagId("123");
    pendingInstruction.setReceivedQuantity(0);
    pendingInstruction.setProjectedReceiveQty(3);
    pendingInstruction.setPrintChildContainerLabels(true);
    List<ContainerDetails> childContainers = new ArrayList<>();
    ContainerDetails containerDetails = new ContainerDetails();
    childContainers.add(containerDetails);
    pendingInstruction.setChildContainers(childContainers);

    Labels labels = new Labels();
    List<String> availableLabels = new ArrayList<>();
    availableLabels.add("1233321");
    availableLabels.add("343222");
    labels.setAvailableLabels(availableLabels);
    labels.setUsedLabels(new ArrayList<>());
    pendingInstruction.setLabels(labels);
    pendingInstruction.setFirstExpiryFirstOut(false);

    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(pendingInstruction));
    doReturn(defaultUpdateInstructionHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    when(receiptCustomRepository.receivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(new ReceiptSummaryQtyByPoAndPoLineResponse("2324341341231", 2, 0l));
    when(configUtils.getFeatureFlagsByFacility(anyString()))
        .thenReturn(defaultFeatureFlagsByFacility);
    List<String> trackingIds = new ArrayList<>();
    trackingIds.add("1233321");
    trackingIds.add("343222");

    when(containerService.getCreatedChildContainerTrackingIds(any(), anyInt(), anyInt()))
        .thenReturn(trackingIds);

    List<Map<String, Object>> printJobs = new ArrayList<>();
    Map<String, Object> printJob = new HashMap<>();
    printJob.put(ReceivingConstants.PRINT_CLIENT_ID_KEY, "OF");
    Map<String, Object> labelData = new HashMap<>();
    labelData.put("key", "ITEM");
    labelData.put("value", "557143449");
    printJob.put("labelData", labelData);
    printJob.put("data", labelData);
    printJobs.add(printJob);

    when(containerService.getOldFormatCreatedChildContainerLabels(any(), anyInt(), anyInt()))
        .thenReturn(printJobs);

    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(this.printJob);

    when(receiptService.createReceiptsFromInstruction(
            any(UpdateInstructionRequest.class), anyString(), anyString()))
        .thenReturn(receipts);

    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(false);

    InstructionResponseImplOld updateInstructionResponse =
        (InstructionResponseImplOld)
            instructionService.updateInstruction(
                instructionId, updateInstructionRequest6, "", httpHeaders);

    assertEquals(updateInstructionResponse.getInstruction().getLabels().getUsedLabels().size(), 2);
    assertEquals(
        updateInstructionResponse.getPrintJob().get(0).get(ReceivingConstants.PRINT_CLIENT_ID_KEY),
        "OF");
    verify(jmsPublisher, times(1))
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    reset(receiptService);
    reset(instructionRepository);
    reset(printJobService);
  }

  @Test
  public void testUpdateInstruction_validateRotateDateAgainstThreshold() throws ReceivingException {
    doReturn(defaultUpdateInstructionHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    when(instructionRepository.findById(anyLong()))
        .thenReturn(Optional.of(MockInstruction.getInstructionWithFirstExpiryFirstOut()));
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    try {
      instructionService.updateInstruction(
          MockInstruction.getInstructionWithFirstExpiryFirstOut().getId(),
          MockUpdateInstructionRequest.getUpdateInstructionRequest(),
          "",
          httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
    }
    reset(instructionRepository);
  }

  @Test
  public void testUpdateInstruction_validateWarehouseMinLifeRemainingToReceive()
      throws ReceivingException {
    doReturn(defaultUpdateInstructionHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    when(instructionRepository.findById(anyLong()))
        .thenReturn(Optional.of(MockInstruction.getInstructionWithFirstExpiryFirstOut()));
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    try {
      instructionService.updateInstruction(
          MockInstruction.getInstructionWithFirstExpiryFirstOut().getId(),
          MockUpdateInstructionRequest.getInvalidUpdateInstructionRequest(),
          "",
          httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
      assertEquals(
          e.getMessage(), String.format(ReceivingException.INVALID_ITEM_ERROR_MSG, "573170821"));
      ;
    }
    reset(instructionRepository);
  }
}
