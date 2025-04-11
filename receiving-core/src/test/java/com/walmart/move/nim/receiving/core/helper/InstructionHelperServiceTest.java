package com.walmart.move.nim.receiving.core.helper;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.builder.FinalizePORequestBodyBuilder;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClientException;
import com.walmart.move.nim.receiving.core.client.inventory.InventoryRestApiClient;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.message.publisher.JMSInstructionPublisher;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaInstructionMessagePublisher;
import com.walmart.move.nim.receiving.core.message.publisher.MessagePublisher;
import com.walmart.move.nim.receiving.core.mock.data.MockContainer;
import com.walmart.move.nim.receiving.core.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.CompleteInstructionRequest;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DocumentLine;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.InstructionResponseImplNew;
import com.walmart.move.nim.receiving.core.model.InstructionResponseImplOld;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.PoLine;
import com.walmart.move.nim.receiving.core.model.RejectPalletRequest;
import com.walmart.move.nim.receiving.core.model.UpdateInstructionRequest;
import com.walmart.move.nim.receiving.core.model.docktag.DockTagInfo;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePORequestBody;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerIdentifier;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerTag;
import com.walmart.move.nim.receiving.core.model.inventory.InventoryOssReceivingRequest;
import com.walmart.move.nim.receiving.core.model.inventory.TargetContainer;
import com.walmart.move.nim.receiving.core.model.inventory.Transfer;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.LabelServiceImpl;
import com.walmart.move.nim.receiving.core.service.ProblemService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.core.service.WitronDeliveryMetaDataService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class InstructionHelperServiceTest extends ReceivingTestBase {

  @InjectMocks private InstructionHelperService instructionHelperService;
  @InjectMocks private JMSInstructionPublisher jmsInstructionPublisher;
  @InjectMocks private KafkaInstructionMessagePublisher kafkaInstructionMessagePublisher;
  @Mock private JmsPublisher jmsPublisher;
  @Mock private WitronDeliveryMetaDataService witronDeliveryMetaDataService;
  @Mock private ContainerService containerService;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private ReceiptService receiptService;
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private InstructionRepository instructionRepository;
  @Mock private FinalizePORequestBodyBuilder finalizePORequestBodyBuilder;
  @Mock private ProblemService problemService;
  @Mock private AppConfig appConfig;
  @Mock private KafkaTemplate securePublisher;
  @Mock private DeliveryService deliveryService;
  @Mock private DeliveryMetaDataService deliveryMetaDataService;
  @Mock private LabelServiceImpl labelServiceImpl;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private GDMRestApiClient gdmRestApiClient;
  @Mock private MessagePublisher messagePublisher;
  @Mock private InventoryRestApiClient inventoryRestApiClient;
  private UpdateInstructionRequest updateInstructionRequest;

  private HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
  private List<DeliveryDocument> deliveryDocuments = MockInstruction.getDeliveryDocuments();
  private List<DeliveryDocument> multiPoPolDeliveryDocuments;
  private Gson gson = new Gson();
  private Container container = MockContainer.getValidContainer();
  private Instruction instruction = MockInstruction.getInstruction();
  private Map<String, Object> mockInstructionContainerMap;
  @Mock protected ReceiptsAggregator receiptsAggregator;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);
    updateInstructionRequest = new UpdateInstructionRequest();
    DocumentLine documentLine1 = new DocumentLine();
    documentLine1.setQuantity(2);
    documentLine1.setPurchaseRefType("CROSSU");
    documentLine1.setVnpkQty(1);
    documentLine1.setWhpkQty(1);
    List<DocumentLine> documentLines1 = new ArrayList<>();
    documentLines1.add(documentLine1);
    updateInstructionRequest.setDeliveryDocumentLines(documentLines1);
    String deliveryDetailsJson = null;
    try {
      String dataPath =
          new File("../receiving-test/src/main/resources/json/MultiPoPol.json").getCanonicalPath();
      deliveryDetailsJson = new String(Files.readAllBytes(Paths.get(dataPath)));
      multiPoPolDeliveryDocuments =
          Arrays.asList(gson.fromJson(deliveryDetailsJson, DeliveryDocument[].class));
    } catch (IOException e) {
      e.printStackTrace();
    }
    when(configUtils.getDCSpecificPODistributionBatchSize(any())).thenReturn(3L);
  }

  @AfterMethod
  public void tearDown() {
    reset(jmsPublisher);
    reset(appConfig);
    reset(securePublisher);
    reset(instructionPersisterService);
    reset(problemService);
    reset(deliveryService);
    reset(deliveryMetaDataService);
    reset(containerPersisterService);
    reset(gdmRestApiClient);
    reset(messagePublisher);
    reset(instructionRepository);
    reset(inventoryRestApiClient);
  }

  @BeforeMethod
  public void beforeMethod() {
    when(configUtils.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.INSTRUCTION_PUBLISHER,
            MessagePublisher.class))
        .thenReturn(jmsInstructionPublisher);
    when(configUtils.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.DELIVERY_METADATA_SERVICE,
            DeliveryMetaDataService.class))
        .thenReturn(deliveryMetaDataService);
    when(appConfig.isRoundRobinPOSelectEnabled()).thenReturn(true);
    mockInstructionContainerMap = new HashMap<>();
    mockInstructionContainerMap.put(ReceivingConstants.INSTRUCTION, instruction);
    mockInstructionContainerMap.put(ReceivingConstants.CONTAINER, container);
  }

  @Test
  public void testPublishInstructionForCreateInstruction() {
    Instruction openInstruction = MockInstruction.getCreatedInstruction();
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    instructionHelperService.publishInstruction(
        openInstruction, null, 50, null, InstructionStatus.CREATED, httpHeaders);

    verify(jmsPublisher, times(1)).publish(any(), any(), any());
    verify(securePublisher, times(0)).send(isA(Message.class));
  }

  @Test
  public void testPublishInstructionForUpdateInstruction() {
    Instruction openInstruction = MockInstruction.getPendingInstruction();
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    instructionHelperService.publishInstruction(
        openInstruction,
        updateInstructionRequest,
        50,
        null,
        InstructionStatus.UPDATED,
        httpHeaders);

    verify(jmsPublisher, times(1)).publish(any(), any(), any());
    verify(securePublisher, times(0)).send(isA(Message.class));
  }

  @Test
  public void testPublishInstructionForUpdateInstructionWithContainer() {
    Instruction openInstruction = MockInstruction.getPendingInstruction();
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    when(appConfig.isPublishContainerDetailsInInstruction()).thenReturn(true);
    instructionHelperService.publishInstruction(
        openInstruction,
        updateInstructionRequest,
        50,
        new Container(),
        InstructionStatus.UPDATED,
        httpHeaders);

    ArgumentCaptor<ReceivingJMSEvent> jmsEventArgumentCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(1)).publish(any(), jmsEventArgumentCaptor.capture(), any());
    ReceivingJMSEvent value = jmsEventArgumentCaptor.getValue();
    assertTrue(value.getMessageBody().contains("container"));
    verify(securePublisher, times(0)).send(isA(Message.class));
  }

  @Test
  public void testPublishInstructionForUpdateInstructionWithContainerFlagDisabled() {
    Instruction openInstruction = MockInstruction.getPendingInstruction();
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    when(appConfig.isPublishContainerDetailsInInstruction()).thenReturn(false);
    instructionHelperService.publishInstruction(
        openInstruction,
        updateInstructionRequest,
        50,
        new Container(),
        InstructionStatus.UPDATED,
        httpHeaders);

    ArgumentCaptor<ReceivingJMSEvent> jmsEventArgumentCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(1)).publish(any(), jmsEventArgumentCaptor.capture(), any());
    ReceivingJMSEvent value = jmsEventArgumentCaptor.getValue();
    assertFalse(value.getMessageBody().contains("container"));
    verify(securePublisher, times(0)).send(isA(Message.class));
  }

  @Test
  public void testPublishInstructionForCompleteInstruction() {
    Instruction openInstruction = MockInstruction.getCompleteInstruction();
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    instructionHelperService.publishInstruction(
        openInstruction, null, 50, null, InstructionStatus.COMPLETED, httpHeaders);

    verify(jmsPublisher, times(1)).publish(any(), any(), any());
    verify(securePublisher, times(0)).send(isA(Message.class));
  }

  @Test
  public void testPublishUpdateInstruction() {
    Instruction openInstruction = MockInstruction.getPendingInstruction();
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    instructionHelperService.publishUpdateInstructionToWFM(
        httpHeaders, openInstruction, updateInstructionRequest, "DA");
    verify(jmsPublisher, times(1)).publish(any(), any(), any());
  }

  @Test
  public void testPublishInstructionForCreateInstructionOnKafka() {
    Instruction openInstruction = MockInstruction.getCreatedInstruction();
    when(configUtils.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.INSTRUCTION_PUBLISHER,
            MessagePublisher.class))
        .thenReturn(kafkaInstructionMessagePublisher);
    instructionHelperService.publishInstruction(
        openInstruction, null, 50, null, InstructionStatus.CREATED, httpHeaders);

    verify(securePublisher, times(1)).send(isA(Message.class));
    verify(jmsPublisher, times(0)).publish(any(), any(), any());
  }

  @Test
  public void testGetReceivedQtyDetails() {
    doReturn(10L).when(receiptService).getReceivedQtyByProblemId(anyString());
    Pair<Integer, Long> receivedQuantityDetails =
        instructionHelperService.getReceivedQtyDetails(
            "DUMMY_PROB_ID", deliveryDocuments.get(0).getDeliveryDocumentLines().get(0));
    assertEquals(receivedQuantityDetails.getKey(), Integer.valueOf(15));
    assertEquals(receivedQuantityDetails.getValue(), Long.valueOf(10L));
  }

  @Test
  public void testGetReceivedQtyDetailsAndValidate_managerOverrideNotEnabledAndNotOverage()
      throws ReceivingException {
    doReturn(false)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE);
    doReturn(10L).when(receiptService).getReceivedQtyByProblemId(anyString());
    Pair<Integer, Long> receivedQuantityDetails =
        instructionHelperService.getReceivedQtyDetailsAndValidate(
            "DUMMY_PROB_ID", deliveryDocuments.get(0), "21119003", false, false);
    assertEquals(receivedQuantityDetails.getKey(), Integer.valueOf(15));
    assertEquals(receivedQuantityDetails.getValue(), Long.valueOf(10L));
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "The allowed number of cases for this item have been received. Please report the remaining as overage.")
  public void testGetReceivedQtyDetailsAndValidate_managerOverrideNotEnabledAndOverage()
      throws ReceivingException {
    doReturn(false)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE);
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", GROCERY_PROBLEM_RECEIVE_FEATURE, false);
    doReturn(15L).when(receiptService).getReceivedQtyByProblemId(anyString());
    Pair<Integer, Long> receivedQuantityDetails =
        instructionHelperService.getReceivedQtyDetailsAndValidate(
            "DUMMY_PROB_ID", deliveryDocuments.get(0), "21119003", false, false);
  }

  @Test
  public void
      testGetReceivedQtyDetailsAndValidate_managerOverrideEnabledAndOverageAndMangerOverrideFromUITrue()
          throws ReceivingException {
    doReturn(null)
        .when(witronDeliveryMetaDataService)
        .doManagerOverride(anyString(), anyString(), anyString(), anyInt(), anyString());
    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE);
    doReturn(15L).when(receiptService).getReceivedQtyByProblemId(anyString());
    Pair<Integer, Long> receivedQuantityDetails =
        instructionHelperService.getReceivedQtyDetailsAndValidate(
            "DUMMY_PROB_ID", deliveryDocuments.get(0), "21119003", false, false);
    assertEquals(receivedQuantityDetails.getKey(), Integer.valueOf(15));
    assertEquals(receivedQuantityDetails.getValue(), Long.valueOf(15L));
  }

  @Test
  public void
      testGetReceivedQtyDetailsAndValidate_managerOverrideEnabledAndOverageAndMangerOverrideFromUIFalseAndEarlierOverrideTrue()
          throws ReceivingException {
    doReturn(true)
        .when(witronDeliveryMetaDataService)
        .isManagerOverride(anyString(), any(), anyInt(), anyString());
    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE);
    doReturn(15L).when(receiptService).getReceivedQtyByProblemId(anyString());
    Pair<Integer, Long> receivedQuantityDetails =
        instructionHelperService.getReceivedQtyDetailsAndValidate(
            "DUMMY_PROB_ID", deliveryDocuments.get(0), "21119003", false, false);
    assertEquals(receivedQuantityDetails.getKey(), Integer.valueOf(15));
    assertEquals(receivedQuantityDetails.getValue(), Long.valueOf(15L));
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "The allowed number of cases for this item have been received. Please report the remaining as overage.")
  public void
      testGetReceivedQtyDetailsAndValidate_managerOverrideEnabledAndOverageAndMangerOverrideFromUIFalseAndEarlierOverrideFalse()
          throws ReceivingException {
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", GROCERY_PROBLEM_RECEIVE_FEATURE, false);
    doReturn(false)
        .when(witronDeliveryMetaDataService)
        .isManagerOverride(anyString(), any(), anyInt(), anyString());
    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE);
    doReturn(15L).when(receiptService).getReceivedQtyByProblemId(anyString());
    Pair<Integer, Long> receivedQuantityDetails =
        instructionHelperService.getReceivedQtyDetailsAndValidate(
            "DUMMY_PROB_ID", deliveryDocuments.get(0), "21119003", false, false);
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testKotlinOverage() throws ReceivingException {
    doReturn(false)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE);
    doReturn(15L).when(receiptService).getReceivedQtyByProblemId(anyString());

    Pair<Integer, Long> receivedQuantityDetails =
        instructionHelperService.getReceivedQtyDetailsAndValidate(
            "DUMMY_PROB_ID", deliveryDocuments.get(0), "21119003", true, false);
    assertEquals(receivedQuantityDetails.getKey(), Integer.valueOf(15));
    assertEquals(receivedQuantityDetails.getValue(), Long.valueOf(15L));
  }

  @Test
  public void testKotlinOverage_withoutProblemId() throws ReceivingException {
    doReturn(false)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE);
    doReturn(15L).when(receiptService).getReceivedQtyByPoAndPoLine(anyString(), anyInt());

    Pair<Integer, Long> receivedQuantityDetails =
        instructionHelperService.getReceivedQtyDetailsAndValidate(
            null, deliveryDocuments.get(0), "21119003", true, false);
    assertEquals(receivedQuantityDetails.getKey(), Integer.valueOf(15));
    assertEquals(receivedQuantityDetails.getValue(), Long.valueOf(15L));
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testKotlinOverage_withProblemId() throws ReceivingException {

    doReturn(false)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE);
    doReturn(15L).when(receiptService).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    doReturn(15L).when(receiptService).getReceivedQtyByProblemId("DUMMY_PROB_ID");
    Pair<Integer, Long> receivedQuantityDetails =
        instructionHelperService.getReceivedQtyDetailsAndValidate(
            "DUMMY_PROB_ID", deliveryDocuments.get(0), "21119003", true, false);
  }

  @Test
  public void testGetReceivedQtyDetailsInEaAndValidate_managerOverrideNotEnabledAndNotOverage()
      throws ReceivingException {
    doReturn(false)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE);
    doReturn(10L).when(receiptService).getReceivedQtyByPoAndPoLineInEach(anyString(), anyInt());
    Pair<Integer, Long> receivedQuantityDetails =
        instructionHelperService.getReceivedQtyDetailsInEaAndValidate(
            "", deliveryDocuments.get(0), "21119003");
    assertEquals(receivedQuantityDetails.getKey(), Integer.valueOf(15));
    assertEquals(receivedQuantityDetails.getValue(), Long.valueOf(10L));
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "The allowed number of cases for this item have been received. Please report the remaining as overage.")
  public void testGetReceivedQtyDetailsInEaAndValidate_managerOverrideNotEnabledAndOverage()
      throws ReceivingException {
    doReturn(false)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE);
    doReturn(15L).when(receiptService).getReceivedQtyByPoAndPoLineInEach(anyString(), anyInt());
    Pair<Integer, Long> receivedQuantityDetails =
        instructionHelperService.getReceivedQtyDetailsInEaAndValidate(
            "", deliveryDocuments.get(0), "21119003");
    assertEquals(receivedQuantityDetails.getKey(), Integer.valueOf(15));
    assertEquals(receivedQuantityDetails.getValue(), Long.valueOf(15L));
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "The allowed number of cases for this item have been received. Please report the remaining as overage.")
  public void
      testGetReceivedQtyDetailsInEaAndValidateForProblem_managerOverrideNotEnabledAndOverage()
          throws ReceivingException {
    doReturn(false)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE);
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", GROCERY_PROBLEM_RECEIVE_FEATURE, false);
    doReturn(15L).when(receiptService).getReceivedQtyByProblemIdInEach(anyString());
    Pair<Integer, Long> receivedQuantityDetails =
        instructionHelperService.getReceivedQtyDetailsInEaAndValidate(
            "DUMMY_PROB_ID", deliveryDocuments.get(0), "21119003");
  }

  @Test
  public void testPrepareInstructionMessage_androidPrintingNotEnabledAndShowRotateDateTrue()
      throws ReceivingException {
    doReturn(true).when(configUtils).isShowRotateDateOnPrintLabelEnabled(any());
    doReturn(false).when(configUtils).isPrintingAndroidComponentEnabled();
    InstructionResponse instructionResponse =
        instructionHelperService.prepareInstructionResponse(
            MockInstruction.getOldPrintOpenInstruction(),
            MockContainer.getSSTKContainer(),
            1234,
            null);
    assert (instructionResponse instanceof InstructionResponseImplOld);
  }

  @Test
  public void testPrepareInstructionMessage_androidPrintingEnabledWithPoConf()
      throws ReceivingException {
    doReturn(true).when(configUtils).isPoConfirmationFlagEnabled(any());
    doReturn(true).when(configUtils).isPrintingAndroidComponentEnabled();
    InstructionResponse instructionResponse =
        instructionHelperService.prepareInstructionResponse(
            MockInstruction.getCompleteInstruction(), MockContainer.getSSTKContainer(), 1234, null);
    assert (instructionResponse instanceof InstructionResponseImplNew);
  }

  @Test
  public void testPrepareInstructionMessage_androidPrintingEnabledWithPoConfDisabled()
      throws ReceivingException {
    doReturn(false).when(configUtils).isPoConfirmationFlagEnabled(any());
    doReturn(true).when(configUtils).isPrintingAndroidComponentEnabled();
    InstructionResponse instructionResponse =
        instructionHelperService.prepareInstructionResponse(
            MockInstruction.getCompleteInstruction(), MockContainer.getSSTKContainer(), 1234, null);
    assert (instructionResponse instanceof InstructionResponseImplNew);
  }

  @Test
  public void testPublishConsolidatedContainer() {
    doNothing().when(containerService).publishContainer(any(), any(), any(Boolean.class));
    instructionHelperService.publishConsolidatedContainer(
        MockContainer.getSSTKContainer(), httpHeaders, true);
    verify(containerService, times(1)).publishContainer(any(), any(), any(Boolean.class));
  }

  @Test
  public void testGetContainerLabelNew() {
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    String labelId = "a00000001234";
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    List<Map<String, Object>> labelDataList = new ArrayList<>();
    Map<String, Object> deliveryMap = new HashMap<>();
    deliveryMap.put("key", "DELIVERYNBR");
    deliveryMap.put("value", "312345");
    labelDataList.add(deliveryMap);
    Map<String, Object> containerLabel =
        instructionHelperService.getContainerLabel(labelId, httpHeaders, labelDataList);
    assertEquals(ReceivingConstants.RECEIVING_PROVIDER_ID, containerLabel.get("clientId"));

    Map<String, String> headers = (Map<String, String>) containerLabel.get("headers");
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        headers.get(ReceivingConstants.TENENT_FACLITYNUM));
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
        headers.get(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
        headers.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

    List<Map<String, Object>> printReqList =
        (List<Map<String, Object>>) containerLabel.get("printRequests");
    Map<String, Object> printReq = printReqList.get(0);
    assertEquals(labelId, printReq.get("labelIdentifier"));
    assertEquals("dock_tag_atlas", printReq.get("formatName"));
    assertEquals(72, printReq.get("ttlInHours"));
    assertEquals(labelDataList, printReq.get("data"));
  }

  @Test
  public void testGetContainerLabelOld() {
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(false);
    String labelId = "a00000001234";
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    List<Map<String, Object>> labelDataList = new ArrayList<>();
    Map<String, Object> deliveryMap = new HashMap<>();
    deliveryMap.put("key", "DELIVERYNBR");
    deliveryMap.put("value", "312345");
    labelDataList.add(deliveryMap);
    Map<String, Object> containerLabel =
        instructionHelperService.getContainerLabel(labelId, httpHeaders, labelDataList);

    assertEquals(labelId, containerLabel.get("labelIdentifier"));
    assertEquals(72, containerLabel.get("ttlInHours"));
    assertEquals(labelDataList, containerLabel.get("data"));
    assertEquals(labelDataList, containerLabel.get("labelData"));
    assertEquals(ReceivingConstants.RECEIVING_PROVIDER_ID, containerLabel.get("clientId"));
    assertEquals(ReceivingConstants.RECEIVING_PROVIDER_ID, containerLabel.get("clientID"));
    assertEquals("dock_tag_atlas", containerLabel.get("formatId"));
    assertEquals("dock_tag_atlas", containerLabel.get("formatID"));
  }

  @Test
  public void testAutoSelectDocumentAndDocumentLine_returnNullIfDeliveryDocumentsIsEmpty() {
    doReturn(new ArrayList<>())
        .when(receiptService)
        .receivedQtyInVNPKByPoAndPoLineList(anyList(), anySet());
    Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLine =
        instructionHelperService.autoSelectDocumentAndDocumentLineMABD(
            new ArrayList<>(), 1, EMPTY_STRING);
    assertNull(autoSelectDocumentAndDocumentLine);
  }

  @Test
  public void testAutoSelectDocumentAndDocumentLine_returnNullIfDeliveryDocumentsExhausted() {
    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.IS_RECEIPT_AGGREGATOR_CHECK_ENABLED);
    List<ReceiptSummaryEachesResponse> receivedQtyResponse = new ArrayList<>();
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse("95259000", 1, null, 10L));
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse("95259002", 1, null, 10L));
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse("95259001", 1, null, 10L));
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse("95259001", 2, null, 10L));
    doReturn(receivedQtyResponse)
        .when(receiptService)
        .receivedQtyByPoAndPoLineList(anyList(), anySet());
    Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLine =
        instructionHelperService.autoSelectDocumentAndDocumentLineMABD(
            multiPoPolDeliveryDocuments, 1, EMPTY_STRING);
    assertNull(autoSelectDocumentAndDocumentLine);
  }

  @Test
  public void
      testAutoSelectDocumentAndDocumentLine_returnFirstByMABDIfAvailableAgainstOrderedQty() {
    List<DeliveryDocument> deliveryDocumentList =
        multiPoPolDeliveryDocuments
            .stream()
            .sorted(
                Comparator.comparing(DeliveryDocument::getPurchaseReferenceMustArriveByDate)
                    .thenComparing(DeliveryDocument::getPurchaseReferenceNumber))
            .collect(Collectors.toList());
    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.IS_RECEIPT_AGGREGATOR_CHECK_ENABLED);
    List<ReceiptSummaryEachesResponse> receivedQtyResponse = new ArrayList<>();
    receivedQtyResponse.add(
        new ReceiptSummaryEachesResponse(
            deliveryDocumentList.get(0).getPurchaseReferenceNumber(),
            deliveryDocumentList
                .get(0)
                .getDeliveryDocumentLines()
                .get(0)
                .getPurchaseReferenceLineNumber(),
            null,
            2L));
    doReturn(receivedQtyResponse)
        .when(receiptService)
        .receivedQtyByPoAndPoLineList(anyList(), anySet());

    Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLine =
        instructionHelperService.autoSelectDocumentAndDocumentLineMABD(
            multiPoPolDeliveryDocuments, 1, EMPTY_STRING);
    assertEquals(
        autoSelectDocumentAndDocumentLine.getKey().getPurchaseReferenceNumber(),
        deliveryDocumentList.get(0).getPurchaseReferenceNumber());
    assertEquals(
        autoSelectDocumentAndDocumentLine.getValue().longValue(),
        autoSelectDocumentAndDocumentLine
                .getKey()
                .getDeliveryDocumentLines()
                .get(0)
                .getTotalOrderQty()
            - 2L);
  }

  @Test
  public void
      testAutoSelectDocumentAndDocumentLine_returnByMABDIfOnlyAvailableAgainstAllowedOvgQty() {
    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.IS_RECEIPT_AGGREGATOR_CHECK_ENABLED);
    List<ReceiptSummaryEachesResponse> receivedQtyResponse = new ArrayList<>();
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse("95259000", 1, null, 4L));
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse("95259002", 1, null, 4L));
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse("95259001", 1, null, 4L));
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse("95259001", 2, null, 4L));
    doReturn(receivedQtyResponse)
        .when(receiptService)
        .receivedQtyByPoAndPoLineList(anyList(), anySet());

    Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLine =
        instructionHelperService.autoSelectDocumentAndDocumentLineMABD(
            multiPoPolDeliveryDocuments, 1, EMPTY_STRING);
    assertEquals(
        autoSelectDocumentAndDocumentLine.getKey().getPurchaseReferenceNumber(), "95259000");
    assertEquals(autoSelectDocumentAndDocumentLine.getValue().longValue(), 2L);
  }

  @Test
  public void testAutoSelectDocumentAndDocumentLine_returnByMABD() {
    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.IS_RECEIPT_AGGREGATOR_CHECK_ENABLED);
    List<ReceiptSummaryEachesResponse> receivedQtyResponse = new ArrayList<>();
    doReturn(receivedQtyResponse)
        .when(receiptService)
        .receivedQtyByPoAndPoLineList(anyList(), anySet());

    Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLine =
        instructionHelperService.autoSelectDocumentAndDocumentLineMABD(
            multiPoPolDeliveryDocuments, 1, EMPTY_STRING);
    assertEquals(
        autoSelectDocumentAndDocumentLine.getKey().getPurchaseReferenceNumber(), "95259000");
    assertEquals(autoSelectDocumentAndDocumentLine.getValue().longValue(), 0L);
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse("95259000", 1, null, 6L));
    doReturn(receivedQtyResponse)
        .when(receiptService)
        .receivedQtyByPoAndPoLineList(anyList(), anySet());
    autoSelectDocumentAndDocumentLine =
        instructionHelperService.autoSelectDocumentAndDocumentLineMABD(
            multiPoPolDeliveryDocuments, 1, EMPTY_STRING);
    assertEquals(
        autoSelectDocumentAndDocumentLine.getKey().getPurchaseReferenceNumber(), "95259002");
    assertEquals(autoSelectDocumentAndDocumentLine.getValue().longValue(), 0L);
  }

  @Test
  public void
      testAutoSelectDocumentAndDocumentLineRoundRobin_returnNullIfDeliveryDocumentsIsEmpty() {
    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.IS_RECEIPT_AGGREGATOR_CHECK_ENABLED);
    List<ReceiptSummaryEachesResponse> deliveryPoLineReceipts = new ArrayList<>();
    doReturn(deliveryPoLineReceipts)
        .when(receiptService)
        .receivedQtyByPoAndPoLinesAndDelivery(anyLong(), anyList(), anySet());
    List<ReceiptSummaryEachesResponse> poLineReceipts = new ArrayList<>();
    doReturn(poLineReceipts).when(receiptService).receivedQtyByPoAndPoLineList(anyList(), anySet());
    Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLineRoundRobin =
        instructionHelperService.autoSelectDocumentAndDocumentLineRoundRobin(new ArrayList<>(), 1);
    assertNull(autoSelectDocumentAndDocumentLineRoundRobin);
  }

  @Test
  public void
      testAutoSelectDocumentAndDocumentLineRoundRobin_returnNullIfDeliveryDocumentsExhausted() {
    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.IS_RECEIPT_AGGREGATOR_CHECK_ENABLED);
    List<ReceiptSummaryEachesResponse> deliveryPoLineReceipts = new ArrayList<>();
    deliveryPoLineReceipts.add(new ReceiptSummaryEachesResponse("95259000", 1, null, 10L));
    deliveryPoLineReceipts.add(new ReceiptSummaryEachesResponse("95259002", 1, null, 10L));
    deliveryPoLineReceipts.add(new ReceiptSummaryEachesResponse("95259001", 1, null, 10L));
    deliveryPoLineReceipts.add(new ReceiptSummaryEachesResponse("95259001", 2, null, 10L));
    doReturn(deliveryPoLineReceipts)
        .when(receiptService)
        .receivedQtyByPoAndPoLinesAndDelivery(anyLong(), anyList(), anySet());
    List<ReceiptSummaryEachesResponse> poLineReceipts = new ArrayList<>();
    poLineReceipts.add(new ReceiptSummaryEachesResponse("95259000", 1, null, 10L));
    poLineReceipts.add(new ReceiptSummaryEachesResponse("95259002", 1, null, 10L));
    poLineReceipts.add(new ReceiptSummaryEachesResponse("95259001", 1, null, 10L));
    poLineReceipts.add(new ReceiptSummaryEachesResponse("95259001", 2, null, 10L));
    doReturn(poLineReceipts).when(receiptService).receivedQtyByPoAndPoLineList(anyList(), anySet());
    Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLineRoundRobin =
        instructionHelperService.autoSelectDocumentAndDocumentLineRoundRobin(
            multiPoPolDeliveryDocuments, 1);
    assertNull(autoSelectDocumentAndDocumentLineRoundRobin);
  }

  @Test
  public void
      testAutoSelectDocumentAndDocumentLineRoundRobin_returnByRoundRobinIfAvailableAgainstOrderedQty() {
    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.IS_RECEIPT_AGGREGATOR_CHECK_ENABLED);
    List<DeliveryDocument> deliveryDocumentList =
        multiPoPolDeliveryDocuments
            .stream()
            .sorted(
                Comparator.comparing(DeliveryDocument::getPurchaseReferenceMustArriveByDate)
                    .thenComparing(DeliveryDocument::getPurchaseReferenceNumber))
            .collect(Collectors.toList());
    List<ReceiptSummaryEachesResponse> deliveryPoLineReceipts = new ArrayList<>();
    deliveryPoLineReceipts.add(new ReceiptSummaryEachesResponse("95259000", 1, null, 3L));
    deliveryPoLineReceipts.add(new ReceiptSummaryEachesResponse("95259002", 1, null, 3L));
    deliveryPoLineReceipts.add(new ReceiptSummaryEachesResponse("95259001", 1, null, 1L));
    doReturn(deliveryPoLineReceipts)
        .when(receiptService)
        .receivedQtyByPoAndPoLinesAndDelivery(anyLong(), anyList(), anySet());
    List<ReceiptSummaryEachesResponse> poLineReceipts = new ArrayList<>();
    poLineReceipts.add(new ReceiptSummaryEachesResponse("95259000", 1, null, 3L));
    poLineReceipts.add(new ReceiptSummaryEachesResponse("95259002", 1, null, 3L));
    poLineReceipts.add(new ReceiptSummaryEachesResponse("95259001", 1, null, 1L));
    doReturn(poLineReceipts).when(receiptService).receivedQtyByPoAndPoLineList(anyList(), anySet());

    Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLineRoundRobin =
        instructionHelperService.autoSelectDocumentAndDocumentLineRoundRobin(
            multiPoPolDeliveryDocuments, 1);
    assertEquals(
        autoSelectDocumentAndDocumentLineRoundRobin.getKey().getPurchaseReferenceNumber(),
        deliveryDocumentList.get(2).getPurchaseReferenceNumber());
    assertEquals(
        autoSelectDocumentAndDocumentLineRoundRobin
            .getKey()
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        deliveryDocumentList
            .get(2)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber());
    assertEquals(
        autoSelectDocumentAndDocumentLineRoundRobin.getValue().longValue(),
        autoSelectDocumentAndDocumentLineRoundRobin
                .getKey()
                .getDeliveryDocumentLines()
                .get(0)
                .getTotalOrderQty()
            - 3L);
  }

  @Test
  public void
      testAutoSelectDocumentAndDocumentLineRoundRobin_returnByFallbackMABDIfAvailableAgainstOrderedQty() {
    when(appConfig.isRoundRobinPOSelectEnabled()).thenReturn(false);
    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.IS_RECEIPT_AGGREGATOR_CHECK_ENABLED);
    List<DeliveryDocument> deliveryDocumentList =
        multiPoPolDeliveryDocuments
            .stream()
            .sorted(
                Comparator.comparing(DeliveryDocument::getPurchaseReferenceMustArriveByDate)
                    .thenComparing(DeliveryDocument::getPurchaseReferenceNumber))
            .collect(Collectors.toList());

    List<ReceiptSummaryEachesResponse> poLineReceipts = new ArrayList<>();
    poLineReceipts.add(new ReceiptSummaryEachesResponse("95259000", 1, null, 2L));
    poLineReceipts.add(new ReceiptSummaryEachesResponse("95259002", 1, null, 2L));
    poLineReceipts.add(new ReceiptSummaryEachesResponse("95259001", 1, null, 2L));
    poLineReceipts.add(new ReceiptSummaryEachesResponse("95259001", 2, null, 2L));
    doReturn(poLineReceipts).when(receiptService).receivedQtyByPoAndPoLineList(anyList(), anySet());

    Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLineRoundRobin =
        instructionHelperService.autoSelectDocumentAndDocumentLineRoundRobin(
            multiPoPolDeliveryDocuments, 4);
    assertEquals(
        autoSelectDocumentAndDocumentLineRoundRobin.getKey().getPurchaseReferenceNumber(),
        deliveryDocumentList.get(0).getPurchaseReferenceNumber());
    assertEquals(
        autoSelectDocumentAndDocumentLineRoundRobin.getValue().longValue(),
        autoSelectDocumentAndDocumentLineRoundRobin
                .getKey()
                .getDeliveryDocumentLines()
                .get(0)
                .getTotalOrderQty()
            - 2L);
  }

  @Test
  public void
      testAutoSelectDocumentAndDocumentLineRoundRobin_returnByFallbackMABDIfAvailableAgainstOrderedQtyOverage() {
    when(appConfig.isRoundRobinPOSelectEnabled()).thenReturn(false);
    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.IS_RECEIPT_AGGREGATOR_CHECK_ENABLED);
    List<DeliveryDocument> deliveryDocumentList =
        multiPoPolDeliveryDocuments
            .stream()
            .sorted(
                Comparator.comparing(DeliveryDocument::getPurchaseReferenceMustArriveByDate)
                    .thenComparing(DeliveryDocument::getPurchaseReferenceNumber))
            .collect(Collectors.toList());
    List<ReceiptSummaryEachesResponse> poLineReceipts = new ArrayList<>();
    poLineReceipts.add(
        new ReceiptSummaryEachesResponse(
            deliveryDocumentList.get(0).getPurchaseReferenceNumber(),
            deliveryDocumentList
                .get(0)
                .getDeliveryDocumentLines()
                .get(0)
                .getPurchaseReferenceLineNumber(),
            null,
            2L));
    doReturn(poLineReceipts).when(receiptService).receivedQtyByPoAndPoLineList(anyList(), anySet());

    Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLineRoundRobin =
        instructionHelperService.autoSelectDocumentAndDocumentLineRoundRobin(
            multiPoPolDeliveryDocuments, 5);
    assertEquals(
        autoSelectDocumentAndDocumentLineRoundRobin.getKey().getPurchaseReferenceNumber(),
        deliveryDocumentList.get(1).getPurchaseReferenceNumber());
    assertEquals(
        autoSelectDocumentAndDocumentLineRoundRobin.getValue().longValue(),
        autoSelectDocumentAndDocumentLineRoundRobin
                .getKey()
                .getDeliveryDocumentLines()
                .get(0)
                .getTotalOrderQty()
            - 3L);
  }

  @Test
  public void
      testAutoSelectDocumentAndDocumentLineRoundRobin_returnByRoundRobinIfOnlyAvailableAgainstAllowedOvgQty() {
    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.IS_RECEIPT_AGGREGATOR_CHECK_ENABLED);
    List<ReceiptSummaryEachesResponse> deliveryPoLineReceipts = new ArrayList<>();
    deliveryPoLineReceipts.add(new ReceiptSummaryEachesResponse("95259000", 1, null, 6L));
    deliveryPoLineReceipts.add(new ReceiptSummaryEachesResponse("95259002", 1, null, 6L));
    deliveryPoLineReceipts.add(new ReceiptSummaryEachesResponse("95259001", 1, null, 6L));
    deliveryPoLineReceipts.add(new ReceiptSummaryEachesResponse("95259001", 2, null, 6L));
    doReturn(deliveryPoLineReceipts)
        .when(receiptService)
        .receivedQtyByPoAndPoLinesAndDelivery(anyLong(), anyList(), anySet());
    List<ReceiptSummaryEachesResponse> poLineReceipts = new ArrayList<>();
    poLineReceipts.add(new ReceiptSummaryEachesResponse("95259000", 1, null, 6L));
    poLineReceipts.add(new ReceiptSummaryEachesResponse("95259002", 1, null, 6L));
    poLineReceipts.add(new ReceiptSummaryEachesResponse("95259001", 1, null, 6L));
    poLineReceipts.add(new ReceiptSummaryEachesResponse("95259001", 2, null, 6L));
    doReturn(poLineReceipts).when(receiptService).receivedQtyByPoAndPoLineList(anyList(), anySet());

    Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLineRoundRobin =
        instructionHelperService.autoSelectDocumentAndDocumentLineRoundRobin(
            multiPoPolDeliveryDocuments, 1);
    assertEquals(
        autoSelectDocumentAndDocumentLineRoundRobin.getKey().getPurchaseReferenceNumber(),
        "95259000");
    assertEquals(autoSelectDocumentAndDocumentLineRoundRobin.getValue().longValue(), 3L);
  }

  @Test
  public void testAutoSelectDocumentAndDocumentLineRoundRobin_returnByRoundRobin() {
    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.IS_RECEIPT_AGGREGATOR_CHECK_ENABLED);
    List<ReceiptSummaryEachesResponse> deliveryPoLineReceipts = new ArrayList<>();
    doReturn(deliveryPoLineReceipts)
        .when(receiptService)
        .receivedQtyByPoAndPoLinesAndDelivery(anyLong(), anyList(), anySet());
    List<ReceiptSummaryEachesResponse> poLineReceipts = new ArrayList<>();
    doReturn(poLineReceipts).when(receiptService).receivedQtyByPoAndPoLineList(anyList(), anySet());
    Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLineRoundRobin =
        instructionHelperService.autoSelectDocumentAndDocumentLineRoundRobin(
            multiPoPolDeliveryDocuments, 1);
    assertEquals(
        autoSelectDocumentAndDocumentLineRoundRobin.getKey().getPurchaseReferenceNumber(),
        "95259000");
    assertEquals(autoSelectDocumentAndDocumentLineRoundRobin.getValue().longValue(), 0L);
    deliveryPoLineReceipts.add(new ReceiptSummaryEachesResponse("95259000", 1, null, 2L));
    doReturn(deliveryPoLineReceipts)
        .when(receiptService)
        .receivedQtyByPoAndPoLinesAndDelivery(anyLong(), anyList(), anySet());
    poLineReceipts.add(new ReceiptSummaryEachesResponse("95259000", 1, null, 2L));
    doReturn(poLineReceipts).when(receiptService).receivedQtyByPoAndPoLineList(anyList(), anySet());
    autoSelectDocumentAndDocumentLineRoundRobin =
        instructionHelperService.autoSelectDocumentAndDocumentLineRoundRobin(
            multiPoPolDeliveryDocuments, 1);
    assertEquals(
        autoSelectDocumentAndDocumentLineRoundRobin.getKey().getPurchaseReferenceNumber(),
        "95259002");
    assertEquals(autoSelectDocumentAndDocumentLineRoundRobin.getValue().longValue(), 0L);
  }

  @Test
  public void testIsManagerOverrideIgnoreExpiry1() {
    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE);

    doReturn(true)
        .when(witronDeliveryMetaDataService)
        .isManagerOverride(anyString(), anyString(), anyInt(), anyString());

    boolean isOverrideExpiry =
        instructionHelperService.isManagerOverrideIgnoreExpiry("763734343", "95259002", true, 1);

    assertTrue(isOverrideExpiry);
  }

  @Test
  public void testIsManagerOverrideIgnoreExpiry2() {
    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE);

    doReturn(false)
        .when(witronDeliveryMetaDataService)
        .isManagerOverride(anyString(), anyString(), anyInt(), anyString());

    boolean isOverrideExpiry =
        instructionHelperService.isManagerOverrideIgnoreExpiry("763734343", "95259002", true, 1);

    assertFalse(isOverrideExpiry);
  }

  @Test
  public void testIsManagerOverrideIgnoreExpiry3() {
    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE);

    doReturn(false)
        .when(witronDeliveryMetaDataService)
        .isManagerOverride(anyString(), anyString(), anyInt(), anyString());

    boolean isOverrideExpiry =
        instructionHelperService.isManagerOverrideIgnoreExpiry("763734343", "95259002", false, 1);

    assertFalse(isOverrideExpiry);
  }

  @Test
  public void testIsManagerOverrideIgnoreOverage1() {
    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE);

    doReturn(true)
        .when(witronDeliveryMetaDataService)
        .isManagerOverride(anyString(), anyString(), anyInt(), anyString());

    boolean isOverrideOverage =
        instructionHelperService.isManagerOverrideIgnoreOverage("763734343", "95259002", 1);

    assertTrue(isOverrideOverage);
  }

  @Test
  public void testIsManagerOverrideIgnoreOverage2() {
    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE);

    doReturn(false)
        .when(witronDeliveryMetaDataService)
        .isManagerOverride(anyString(), anyString(), anyInt(), anyString());

    boolean isOverrideOverage =
        instructionHelperService.isManagerOverrideIgnoreOverage("763734343", "95259002", 1);

    assertFalse(isOverrideOverage);
  }

  @Test
  public void testIsManagerOverrideIgnoreOverage3() {
    doReturn(false)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE);

    boolean isOverrideOverage =
        instructionHelperService.isManagerOverrideIgnoreOverage("763734343", "95259002", 1);

    assertFalse(isOverrideOverage);
  }

  @Test
  public void testGetReceivedQtyDetailsAndValidate_isGroceryProblemReceive_Enabled()
      throws ReceivingException {
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", GROCERY_PROBLEM_RECEIVE_FEATURE, false);
    // totalReceivedQty is same as maxReceiveQty = 15 will throw error in bau but test to suppress
    // if isGroceryProblemReceiveEnabled
    doReturn(15L).when(receiptService).getReceivedQtyByProblemId(anyString());
    final DeliveryDocument deliveryDocument = deliveryDocuments.get(0);
    Pair<Integer, Long> receivedQuantityDetails =
        instructionHelperService.getReceivedQtyDetailsAndValidate(
            "DUMMY_PROB_ID", deliveryDocument, "21119003", false, false);
    assertTrue(true);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "The allowed number of cases for this item have been received. Please report the remaining as overage.")
  public void testGetReceivedQtyDetailsAndValidate_isGroceryProblemReceive_Diabled()
      throws ReceivingException {
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", GROCERY_PROBLEM_RECEIVE_FEATURE, false);
    // totalReceivedQty is same as maxReceiveQty = 15 will throw error in bau but test to suppress
    // if isGroceryProblemReceiveEnabled
    doReturn(15L).when(receiptService).getReceivedQtyByProblemId(anyString());
    final DeliveryDocument deliveryDocument = deliveryDocuments.get(0);
    Pair<Integer, Long> receivedQuantityDetails =
        instructionHelperService.getReceivedQtyDetailsAndValidate(
            "DUMMY_PROB_ID", deliveryDocument, "21119003", false, false);
    assertTrue(false);
  }

  @Test
  public void test_transferInstructions() {

    doNothing()
        .when(instructionPersisterService)
        .updateLastChangeUserIdAndLastChangeTs(anyList(), anyString());

    instructionHelperService.transferInstructions(Arrays.asList(1234l), "MOCK_USER");

    verify(instructionPersisterService, times(1))
        .updateLastChangeUserIdAndLastChangeTs(anyList(), anyString());
  }

  @Test
  public void testCreateContainerWithNormalFlow() throws ReceivingException {
    doNothing()
        .when(instructionPersisterService)
        .createContainersAndPrintJobs(
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyString(),
            any(Instruction.class),
            anyInt(),
            anyInt(),
            any());

    Map<String, Object> mockMap = new HashMap<>();
    mockMap.put("instruction", new Instruction());
    mockMap.put("container", new Container());
    doReturn(mockMap)
        .when(instructionPersisterService)
        .completeAndCreatePrintJob(any(HttpHeaders.class), any(Instruction.class));

    Map<String, Object> responseMap =
        instructionHelperService.receiveInstructionAndCompleteProblemTag(
            MockInstruction.getPendingInstruction(),
            updateInstructionRequest,
            1,
            httpHeaders,
            false,
            null);

    verify(instructionPersisterService, times(1))
        .createContainersAndPrintJobs(
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyString(),
            any(Instruction.class),
            anyInt(),
            anyInt(),
            any());

    verify(instructionPersisterService, times(1))
        .completeAndCreatePrintJob(any(HttpHeaders.class), any(Instruction.class));

    assertEquals(responseMap.size(), 2);
  }

  @Test
  public void testCreateContainerWithNormalFlow_updateContainer() throws ReceivingException {
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.IS_UPDATE_CONTAINER_LABEL_ENABLED))
        .thenReturn(true);
    doNothing()
        .when(instructionPersisterService)
        .createContainersAndPrintJobs(
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyString(),
            any(Instruction.class),
            anyInt(),
            anyInt(),
            any());

    Map<String, Object> mockMap = new HashMap<>();
    mockMap.put("instruction", new Instruction());
    mockMap.put("container", new Container());

    doReturn(mockMap)
        .when(instructionPersisterService)
        .completeAndCreatePrintJob(any(HttpHeaders.class), any(Instruction.class));

    Map<String, Object> responseMap =
        instructionHelperService.receiveInstructionAndCompleteProblemTag(
            MockInstruction.getPendingInstruction(),
            updateInstructionRequest,
            1,
            httpHeaders,
            false,
            null);

    verify(instructionPersisterService, times(1))
        .createContainersAndPrintJobs(
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyString(),
            any(Instruction.class),
            anyInt(),
            anyInt(),
            any());

    verify(instructionPersisterService, times(1))
        .completeAndCreatePrintJob(any(HttpHeaders.class), any(Instruction.class));

    assertEquals(responseMap.size(), 2);
  }

  @Test
  public void testCreateContainerWithProblemsFlow() throws ReceivingException {
    Instruction mockInstruction = MockInstruction.getPendingInstruction();
    mockInstruction.setProblemTagId("326120000001");

    doNothing()
        .when(instructionPersisterService)
        .createContainersAndPrintJobs(
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyString(),
            any(Instruction.class),
            anyInt(),
            anyInt(),
            any());

    Map<String, Object> mockMap = new HashMap<>();
    mockMap.put("instruction", new Instruction());
    mockMap.put("container", new Container());
    doReturn(mockMap)
        .when(instructionPersisterService)
        .completeAndCreatePrintJob(any(HttpHeaders.class), any(Instruction.class));

    doReturn(problemService)
        .when(configUtils)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.PROBLEM_SERVICE), eq(ProblemService.class));
    doNothing().when(problemService).completeProblem(any(Instruction.class));

    Map<String, Object> responseMap =
        instructionHelperService.receiveInstructionAndCompleteProblemTag(
            mockInstruction, updateInstructionRequest, 1, httpHeaders, false, null);

    verify(instructionPersisterService, times(1))
        .createContainersAndPrintJobs(
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyString(),
            any(Instruction.class),
            anyInt(),
            anyInt(),
            any());

    verify(instructionPersisterService, times(1))
        .completeAndCreatePrintJob(any(HttpHeaders.class), any(Instruction.class));
    verify(problemService, times(1)).completeProblem(any(Instruction.class));

    assertEquals(responseMap.size(), 2);
  }

  @Test
  public void testCreateContainerWithCorrectionFlow() throws ReceivingException {
    doNothing()
        .when(instructionPersisterService)
        .createContainersAndPrintJobs(
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyString(),
            any(Instruction.class),
            anyInt(),
            anyInt(),
            any());

    Map<String, Object> mockMap = new HashMap<>();
    mockMap.put("instruction", new Instruction());
    mockMap.put("container", new Container());
    doReturn(mockMap)
        .when(instructionPersisterService)
        .completeAndCreatePrintJob(any(HttpHeaders.class), any(Instruction.class));

    doReturn(new FinalizePORequestBody())
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(Map.class));

    Map<String, Object> responseMap =
        instructionHelperService.receiveInstructionAndCompleteProblemTag(
            MockInstruction.getPendingInstruction(),
            updateInstructionRequest,
            1,
            httpHeaders,
            true,
            null);

    verify(instructionPersisterService, times(1))
        .createContainersAndPrintJobs(
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyString(),
            any(Instruction.class),
            anyInt(),
            anyInt(),
            any());

    verify(instructionPersisterService, times(1))
        .completeAndCreatePrintJob(any(HttpHeaders.class), any(Instruction.class));

    assertEquals(responseMap.size(), 3);
  }

  @Test
  public void testReopenDeliveryIfNeeded_WRK() throws ReceivingException {
    doNothing().when(deliveryMetaDataService).findAndUpdateDeliveryStatus(anyString(), any());
    instructionHelperService.reopenDeliveryIfNeeded(12345678L, "WRK", httpHeaders, "WRK");
    verify(deliveryService, times(0)).reOpenDelivery(anyLong(), any());
    verify(deliveryMetaDataService, times(0)).findAndUpdateDeliveryStatus(anyString(), any());
  }

  @Test
  public void testReopenDeliveryIfNeeded_OPN() throws ReceivingException {
    doNothing().when(deliveryMetaDataService).findAndUpdateDeliveryStatus(anyString(), any());
    instructionHelperService.reopenDeliveryIfNeeded(12345678L, "OPN", httpHeaders, "OPN");
    verify(deliveryService, times(0)).reOpenDelivery(anyLong(), any());
    verify(deliveryMetaDataService, times(0)).findAndUpdateDeliveryStatus(anyString(), any());
  }

  @Test
  public void testReopenDeliveryIfNeeded_PNDFNL() throws ReceivingException {
    doNothing().when(deliveryMetaDataService).findAndUpdateDeliveryStatus(anyString(), any());
    instructionHelperService.reopenDeliveryIfNeeded(12345678L, "PNDFNL", httpHeaders, "PNDFNL");
    verify(deliveryService, times(1)).reOpenDelivery(anyLong(), any());
    verify(deliveryMetaDataService, times(1)).findAndUpdateDeliveryStatus(anyString(), any());
  }

  @Test
  public void testReopenDeliveryIfNeeded_PNDPT() throws ReceivingException {
    doNothing().when(deliveryMetaDataService).findAndUpdateDeliveryStatus(anyString(), any());
    instructionHelperService.reopenDeliveryIfNeeded(12345678L, "OPN", httpHeaders, "PNDPT");
    verify(deliveryService, times(1)).reOpenDelivery(anyLong(), any());
    verify(deliveryMetaDataService, times(1)).findAndUpdateDeliveryStatus(anyString(), any());
  }

  @Test
  public void testReopenDeliveryIfNeeded_REO() throws ReceivingException {
    doNothing().when(deliveryMetaDataService).findAndUpdateDeliveryStatus(anyString(), any());
    instructionHelperService.reopenDeliveryIfNeeded(12345678L, "OPN", httpHeaders, "REO");
    verify(deliveryService, times(0)).reOpenDelivery(anyLong(), any());
    verify(deliveryMetaDataService, times(0)).findAndUpdateDeliveryStatus(anyString(), any());
  }

  @Test
  public void testReopenDeliveryIfNeeded_PNDDT() throws ReceivingException {
    doNothing().when(deliveryMetaDataService).findAndUpdateDeliveryStatus(anyString(), any());
    instructionHelperService.reopenDeliveryIfNeeded(12345678L, "OPN", httpHeaders, "PNDDT");
    verify(deliveryService, times(0)).reOpenDelivery(anyLong(), any());
    verify(deliveryMetaDataService, times(0)).findAndUpdateDeliveryStatus(anyString(), any());
  }

  @Test
  public void testReopenDeliveryIfNeeded_FNL() throws ReceivingException {
    doNothing().when(deliveryMetaDataService).findAndUpdateDeliveryStatus(anyString(), any());
    instructionHelperService.reopenDeliveryIfNeeded(12345678L, "FNL", httpHeaders, "FNL");
    verify(deliveryService, times(0)).reOpenDelivery(anyLong(), any());
    verify(deliveryMetaDataService, times(0)).findAndUpdateDeliveryStatus(anyString(), any());
  }

  @Test
  public void testPublishInstructionForUpdateInstruction_WithContainerItems() {
    Instruction openInstruction = MockInstruction.getPendingInstruction();
    when(configUtils.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.INSTRUCTION_PUBLISHER,
            MessagePublisher.class))
        .thenReturn(kafkaInstructionMessagePublisher);
    instructionHelperService.publishInstruction(
        openInstruction,
        updateInstructionRequest,
        50,
        MockContainer.getSSTKContainer(),
        InstructionStatus.UPDATED,
        httpHeaders);

    verify(jmsPublisher, times(0)).publish(any(), any(), any());
    verify(securePublisher, times(1)).send(isA(Message.class));
  }

  @Test
  public void test_completeInstructionAndCreateContainerAndReceipt() throws ReceivingException {
    Instruction openInstruction = MockInstruction.getPendingInstruction();
    final Container container = MockContainer.getContainer();
    HashMap<String, Object> map = new HashMap<>();
    map.put(CONTAINER, container);
    doReturn(map)
        .when(instructionPersisterService)
        .completeInstructionAndContainer(any(HttpHeaders.class), any(Instruction.class));
    PoLine poLineRequest = new PoLine();
    poLineRequest.setRejectQty(1);
    instructionHelperService.completeInstructionAndCreateContainerAndReceiptAndReject(
        poLineRequest,
        openInstruction,
        updateInstructionRequest,
        httpHeaders,
        Arrays.asList("TEST1234"));

    // verify
    verify(instructionPersisterService, times(1))
        .updateInstructionAndCreateContainerAndReceipt(
            any(PoLine.class),
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            any(Instruction.class),
            anyInt(),
            anyList());
    verify(instructionPersisterService, times(1))
        .completeInstructionAndContainer(any(HttpHeaders.class), any(Instruction.class));
  }

  @Test
  public void test_completeInstructionAndCreateContainerAndReceipt_NoRejectQty()
      throws ReceivingException {
    Instruction openInstruction = MockInstruction.getPendingInstruction();
    final Container container = MockContainer.getContainer();
    HashMap<String, Object> map = new HashMap<>();
    map.put(CONTAINER, container);
    doReturn(map)
        .when(instructionPersisterService)
        .completeInstructionAndContainer(any(HttpHeaders.class), any(Instruction.class));
    PoLine poLineRequest = new PoLine();
    poLineRequest.setRejectQty(0);
    instructionHelperService.completeInstructionAndCreateContainerAndReceiptAndReject(
        poLineRequest,
        openInstruction,
        updateInstructionRequest,
        httpHeaders,
        Arrays.asList("TEST1234"));

    // verify
    verify(instructionPersisterService, times(1))
        .updateInstructionAndCreateContainerAndReceipt(
            any(PoLine.class),
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            any(Instruction.class),
            anyInt(),
            anyList());
    verify(instructionPersisterService, times(1))
        .completeInstructionAndContainer(any(HttpHeaders.class), any(Instruction.class));

    verify(deliveryService, times(0)).recordPalletReject(any(RejectPalletRequest.class));
  }

  @Test
  public void testReceiveInstructionAndCompleteProblemTagAndPostInvIfOSS_positive1()
      throws ReceivingException {
    doNothing()
        .when(instructionPersisterService)
        .createContainersAndPrintJobs(
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyString(),
            any(Instruction.class),
            anyInt(),
            anyInt(),
            any());

    Map<String, Object> mockMap = new HashMap<>();
    mockMap.put("instruction", new Instruction());
    mockMap.put("container", new Container());
    doReturn(mockInstructionContainerMap)
        .when(instructionPersisterService)
        .completeAndCreatePrintJob(any(HttpHeaders.class), any(Instruction.class));

    Map<String, Object> responseMap =
        instructionHelperService.receiveInstructionAndCompleteProblemTagAndPostInvIfOSS(
            MockInstruction.getPendingInstruction(),
            updateInstructionRequest,
            1,
            httpHeaders,
            false,
            false,
            false);

    verify(instructionPersisterService, times(1))
        .createContainersAndPrintJobs(
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyString(),
            any(Instruction.class),
            anyInt(),
            anyInt(),
            any());

    verify(instructionPersisterService, times(1))
        .completeAndCreatePrintJob(any(HttpHeaders.class), any(Instruction.class));

    assertEquals(responseMap.size(), 2);
  }

  @Test
  public void testReceiveInstructionAndCompleteProblemTagAndPostInvIfOSS_positive2()
      throws ReceivingException {
    doNothing()
        .when(instructionPersisterService)
        .createContainersAndPrintJobs(
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyString(),
            any(Instruction.class),
            anyInt(),
            anyInt(),
            any());

    doReturn("28")
        .when(configUtils)
        .getCcmValue(
            anyInt(), eq(ELIGIBLE_TRANSFER_POS_CCM_CONFIG), eq(DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE));
    doReturn(mockInstructionContainerMap)
        .when(instructionPersisterService)
        .completeAndCreatePrintJob(any(HttpHeaders.class), any(Instruction.class));
    doNothing()
        .when(inventoryRestApiClient)
        .postInventoryOssReceiving(any(InventoryOssReceivingRequest.class), any(HttpHeaders.class));

    Map<String, Object> responseMap =
        instructionHelperService.receiveInstructionAndCompleteProblemTagAndPostInvIfOSS(
            MockInstruction.getPendingInstruction(),
            updateInstructionRequest,
            1,
            httpHeaders,
            false,
            true,
            false);

    verify(instructionPersisterService, times(1))
        .createContainersAndPrintJobs(
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyString(),
            any(Instruction.class),
            anyInt(),
            anyInt(),
            any());

    verify(instructionPersisterService, times(1))
        .completeAndCreatePrintJob(any(HttpHeaders.class), any(Instruction.class));

    assertEquals(responseMap.size(), 2);
    verify(inventoryRestApiClient, times(1))
        .postInventoryOssReceiving(any(InventoryOssReceivingRequest.class), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testReceiveInstructionAndCompleteProblemTagAndPostInvIfOSS_negative1()
      throws ReceivingException {
    doNothing()
        .when(instructionPersisterService)
        .createContainersAndPrintJobs(
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyString(),
            any(Instruction.class),
            anyInt(),
            anyInt(),
            any());

    doReturn("28")
        .when(configUtils)
        .getCcmValue(
            anyInt(), eq(ELIGIBLE_TRANSFER_POS_CCM_CONFIG), eq(DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE));
    doReturn(mockInstructionContainerMap)
        .when(instructionPersisterService)
        .completeAndCreatePrintJob(any(HttpHeaders.class), any(Instruction.class));
    doThrow(
            new ReceivingException(
                ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_MSG,
                SERVICE_UNAVAILABLE,
                ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_CODE,
                ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_MSG))
        .when(inventoryRestApiClient)
        .postInventoryOssReceiving(any(InventoryOssReceivingRequest.class), any(HttpHeaders.class));

    Map<String, Object> responseMap =
        instructionHelperService.receiveInstructionAndCompleteProblemTagAndPostInvIfOSS(
            MockInstruction.getPendingInstruction(),
            updateInstructionRequest,
            1,
            httpHeaders,
            false,
            true,
            false);
  }

  @Test
  public void testGetInventoryOssReceivingRequest() {
    final Container validContainer = MockContainer.getValidContainer();
    final Map<String, String> destinations = validContainer.getDestination();
    destinations.put(SLOT_TYPE, PRIME);
    InventoryOssReceivingRequest inventoryOssReceivingRequest =
        instructionHelperService.getInventoryOssReceivingRequest(validContainer, "0799800739", 1);
    final Transfer transfer = inventoryOssReceivingRequest.getTransfer();
    final TargetContainer tContainer = transfer.getTargetContainer();
    final Optional<ContainerTag> primeSlot =
        tContainer
            .getContainerTag()
            .tags
            .stream()
            .filter(t -> t.getTag().equals(PUTAWAY_TO_PRIME))
            .findFirst();
    assertEquals(primeSlot.get().getTag(), PUTAWAY_TO_PRIME);
    final ContainerIdentifier containerIdentifier =
        transfer.getSource().getCtnrIdentifiers().get(0);
    assertEquals(containerIdentifier.getIdentifierType(), "ORG_UNIT_ID");
    assertEquals(containerIdentifier.getIdentifierValue(), "5");
  }

  @Test
  public void testCreateAndPublishDockTagInfo_HappyPath()
      throws ReceivingInternalException, GDMRestApiClientException {
    Instruction instruction = MockInstruction.getInstruction();
    CompleteInstructionRequest instructionRequest = new CompleteInstructionRequest();
    instructionRequest.setSkuIndicator("MULTI");
    instructionRequest.setDoorNumber("123");
    Delivery delivery = Delivery.builder().priority(1).build();
    when(gdmRestApiClient.getDeliveryWithDeliveryResponse(anyLong(), any())).thenReturn(delivery);
    when(configUtils.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.DOCKTAG_INFO_PUBLISHER,
            MessagePublisher.class))
        .thenReturn(messagePublisher);
    instructionHelperService.createAndPublishDockTagInfo(instruction, instructionRequest);
    verify(containerPersisterService, times(0)).getContainerDetails(anyString());
    verify(gdmRestApiClient, times(1)).getDeliveryWithDeliveryResponse(anyLong(), any());
    verify(messagePublisher, times(1)).publish(any(DockTagInfo.class), anyMap());
  }

  @Test
  public void testCreateAndPublishDockTagInfo_RequestPayloadIsNull()
      throws ReceivingInternalException, GDMRestApiClientException {
    Instruction instruction = MockInstruction.getInstruction();
    Container container = Container.builder().location("123").build();
    Delivery delivery = Delivery.builder().priority(1).build();
    when(containerPersisterService.getContainerDetails(anyString())).thenReturn(container);
    when(gdmRestApiClient.getDeliveryWithDeliveryResponse(anyLong(), any())).thenReturn(delivery);
    when(configUtils.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.DOCKTAG_INFO_PUBLISHER,
            MessagePublisher.class))
        .thenReturn(messagePublisher);
    instructionHelperService.createAndPublishDockTagInfo(instruction, null);
    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
    verify(gdmRestApiClient, times(1)).getDeliveryWithDeliveryResponse(anyLong(), any());
    verify(messagePublisher, times(1)).publish(any(DockTagInfo.class), anyMap());
  }

  @Test
  public void testCreateAndPublishDockTagInfo_ContainerLocationIsNotFound()
      throws ReceivingInternalException, GDMRestApiClientException {
    Instruction instruction = MockInstruction.getInstruction();
    Delivery delivery = Delivery.builder().priority(1).build();
    when(containerPersisterService.getContainerDetails(anyString())).thenReturn(new Container());
    when(gdmRestApiClient.getDeliveryWithDeliveryResponse(anyLong(), any())).thenReturn(delivery);
    when(configUtils.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.DOCKTAG_INFO_PUBLISHER,
            MessagePublisher.class))
        .thenReturn(messagePublisher);
    instructionHelperService.createAndPublishDockTagInfo(instruction, null);
    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
    verify(gdmRestApiClient, times(1)).getDeliveryWithDeliveryResponse(anyLong(), any());
    verify(messagePublisher, times(1)).publish(any(DockTagInfo.class), anyMap());
  }

  @Test
  public void testCreateAndPublishDockTagInfo_ThrowsExceptionWhenFetchingDelivery()
      throws ReceivingInternalException, GDMRestApiClientException {
    Instruction instruction = MockInstruction.getInstruction();
    Container container = Container.builder().location("123").build();
    when(containerPersisterService.getContainerDetails(anyString())).thenReturn(container);
    when(gdmRestApiClient.getDeliveryWithDeliveryResponse(anyLong(), any()))
        .thenThrow(GDMRestApiClientException.class);
    when(configUtils.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.DOCKTAG_INFO_PUBLISHER,
            MessagePublisher.class))
        .thenReturn(messagePublisher);
    instructionHelperService.createAndPublishDockTagInfo(instruction, null);
    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
    verify(gdmRestApiClient, times(1)).getDeliveryWithDeliveryResponse(anyLong(), any());
    verify(messagePublisher, times(0)).publish(any(DockTagInfo.class), anyMap());
  }
}
