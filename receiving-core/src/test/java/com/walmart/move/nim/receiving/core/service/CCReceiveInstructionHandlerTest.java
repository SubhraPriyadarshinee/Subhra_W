package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.validators.InstructionStateValidator;
import com.walmart.move.nim.receiving.core.common.validators.PurchaseReferenceValidator;
import com.walmart.move.nim.receiving.core.common.validators.WeightThresholdValidator;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.mock.data.MockCCMessageData;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.InstructionResponseImplNew;
import com.walmart.move.nim.receiving.core.model.ReceiveInstructionRequest;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mockito.*;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class CCReceiveInstructionHandlerTest {

  @InjectMocks private CCReceiveInstructionHandler ccReceiveInstructionHandler;
  @Mock private GdcPutawayPublisher gdcPutawayPublisher;
  @Mock private ReceiptService receiptService;
  @Mock private InstructionService instructionService;
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private PurchaseReferenceValidator purchaseReferenceValidator;
  @Spy private InstructionStateValidator instructionStateValidator;
  @Mock private InstructionHelperService instructionHelperService;
  @Mock private ReceiptPublisher receiptPublisher;
  @Mock private ContainerService containerService;
  @Mock private DCFinService dcFinService;
  @Mock private MovePublisher movePublisher;
  @Mock private LabelServiceImpl labelServiceImpl;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private ReceivingUtils receivingUtils;
  @Mock private SorterPublisher sorterPublisher;
  @Mock private WeightThresholdValidator weightThresholdValidator;
  private Instruction instructionFromDB;
  private Gson gson;

  private ReceiveInstructionRequest receiveInstructionRequest;
  private final HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
  private Container container;

  @BeforeClass
  public void initMocks() {
    gson = new Gson();
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(ccReceiveInstructionHandler, "configUtils", configUtils);
    ReflectionTestUtils.setField(ccReceiveInstructionHandler, "gson", gson);
    TenantContext.setFacilityNum(32899);
    TenantContext.setFacilityCountryCode("US");
  }

  @BeforeMethod
  public void setUpTestData() {
    receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDoorNumber("105");
    receiveInstructionRequest.setDeliveryNumber(201901100L);
    receiveInstructionRequest.setQuantity(1);
    receiveInstructionRequest.setQuantityUOM("ZA");

    String inputInstructionJson =
        MockCCMessageData.getMockFileJson(
            "../receiving-test/src/main/resources/" + "json/CCReceiveInstruction.json");

    // TODO: TS is having issue with the conversion
    instructionFromDB =
        new GsonBuilder()
            .registerTypeAdapter(
                Date.class,
                (JsonDeserializer<Date>)
                    (jsonElement, type, context) ->
                        new Date(jsonElement.getAsJsonPrimitive().getAsLong()))
            .create()
            .fromJson(inputInstructionJson, Instruction.class);

    doNothing().when(weightThresholdValidator).validate(any(), anyInt(), anyInt(), anyString());
  }

  @AfterMethod
  public void tearDown() {
    reset(configUtils);
    reset(gdcPutawayPublisher);
    reset(instructionPersisterService);
    reset(purchaseReferenceValidator);
    reset(instructionStateValidator);
    reset(instructionHelperService);
    reset(receiptService);
    reset(containerService);
    reset(instructionService);
    reset(movePublisher);
    reset(receiptPublisher);
    reset(sorterPublisher);
  }

  @Test
  public void test_receiveDelivery() throws ReceivingException {

    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instructionFromDB);
    Map<String, Object> caseLabelsInfo =
        gson.fromJson(
            MockCCMessageData.getMockFileJson(
                "../receiving-test/src/main/resources/" + "json/CCChildContainerLabels_1.json"),
            Map.class);
    when(instructionPersisterService.getPrintlabeldata(any(), anyInt(), anyInt(), any()))
        .thenReturn(caseLabelsInfo);

    container =
        new GsonBuilder()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter())
            .create()
            .fromJson(
                MockCCMessageData.getMockFileJson(
                    "../receiving-test/src/main/resources/" + "json/CCContainer.json"),
                Container.class);
    HashMap<String, Object> map = new HashMap<>();
    map.put("instruction", instructionFromDB);
    map.put("container", container);

    when(containerService.containerComplete(anyString(), anyString())).thenReturn(container);
    when(containerService.getContainerIncludingChild(any())).thenReturn(container);
    when(configUtils.getConfiguredInstance(
            "32899", ReceivingConstants.DC_FIN_SERVICE, DCFinService.class))
        .thenReturn(dcFinService);
    when(instructionHelperService.receiveInstructionAndCompleteProblemTag(
            any(), any(), anyInt(), any(), anyBoolean(), any()))
        .thenReturn(map);

    doReturn(10L).when(receiptService).getReceivedQtyByProblemId(any());
    doReturn(10L)
        .when(receiptService)
        .getReceivedQtyByPoAndPoLine(
            instructionFromDB.getPurchaseReferenceNumber(),
            instructionFromDB.getPurchaseReferenceLineNumber());

    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.MOVE_DEST_BU_ENABLED))
        .thenReturn(false);
    InstructionResponseImplNew response =
        (InstructionResponseImplNew)
            ccReceiveInstructionHandler.receiveInstruction(
                515355L, receiveInstructionRequest, httpHeaders);
    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getInstruction());
    Assert.assertNotNull(response.getPrintJob());
    Assert.assertEquals(
        ((List<Object>) response.getPrintJob().get(ReceivingConstants.PRINT_REQUEST_KEY)).size(),
        2);
  }

  @Test
  public void test_receiveDelivery_ContainerLabel() throws ReceivingException {

    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instructionFromDB);
    Map<String, Object> caseLabelsInfo =
        gson.fromJson(
            MockCCMessageData.getMockFileJson(
                "../receiving-test/src/main/resources/" + "json/CCChildContainerLabels_1.json"),
            Map.class);
    when(instructionPersisterService.getPrintlabeldata(any(), anyInt(), anyInt(), any()))
        .thenReturn(caseLabelsInfo);

    container =
        new GsonBuilder()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter())
            .create()
            .fromJson(
                MockCCMessageData.getMockFileJson(
                    "../receiving-test/src/main/resources/" + "json/CCContainer.json"),
                Container.class);
    HashMap<String, Object> map = new HashMap<>();
    map.put("instruction", instructionFromDB);
    map.put("container", container);

    when(containerService.containerComplete(anyString(), anyString())).thenReturn(container);
    when(containerService.getContainerIncludingChild(any())).thenReturn(container);
    when(configUtils.getConfiguredInstance(
            "32899", ReceivingConstants.DC_FIN_SERVICE, DCFinService.class))
        .thenReturn(dcFinService);

    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.IS_UPDATE_CONTAINER_LABEL_ENABLED))
        .thenReturn(true);
    when(instructionHelperService.receiveInstructionAndCompleteProblemTag(
            any(), any(), anyInt(), any(), anyBoolean(), any()))
        .thenReturn(map);

    doReturn(10L).when(receiptService).getReceivedQtyByProblemId(any());
    doReturn(10L)
        .when(receiptService)
        .getReceivedQtyByPoAndPoLine(
            instructionFromDB.getPurchaseReferenceNumber(),
            instructionFromDB.getPurchaseReferenceLineNumber());

    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.MOVE_DEST_BU_ENABLED))
        .thenReturn(false);
    InstructionResponseImplNew response =
        (InstructionResponseImplNew)
            ccReceiveInstructionHandler.receiveInstruction(
                515355L, receiveInstructionRequest, httpHeaders);
    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getInstruction());
    Assert.assertNotNull(response.getPrintJob());
    Assert.assertEquals(
        ((List<Object>) response.getPrintJob().get(ReceivingConstants.PRINT_REQUEST_KEY)).size(),
        2);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "Instruction is owned by mdl. Please transfer ownership of the instruction before proceeding.*")
  public void test_receiveDelivery_validateUser() throws ReceivingException {

    instructionFromDB.setLastChangeUserId("mdl");
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instructionFromDB);
    ccReceiveInstructionHandler.receiveInstruction(515355L, receiveInstructionRequest, httpHeaders);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "This pallet was completed by mdl, please start a new pallet to continue receiving.*")
  public void test_receiveDelivery_verifyComplete() throws ReceivingException {

    instructionFromDB.setCompleteTs(new Date());
    instructionFromDB.setCompleteUserId("mdl");
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instructionFromDB);
    ccReceiveInstructionHandler.receiveInstruction(515355L, receiveInstructionRequest, httpHeaders);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "This pallet was cancelled by mdl, please start a new pallet to continue receiving.*")
  public void test_receiveDelivery_verifyCompleteCancel() throws ReceivingException {

    instructionFromDB.setCompleteTs(new Date());
    instructionFromDB.setCompleteUserId("mdl");
    instructionFromDB.setReceivedQuantity(0);
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instructionFromDB);

    ccReceiveInstructionHandler.receiveInstruction(515355L, receiveInstructionRequest, httpHeaders);
  }

  @Test
  public void test_receiveDelivery_With_OverageAllowedQty() throws ReceivingException {

    //    Instruction tempInstruction = SerializationUtils.clone(instructionFromDB);

    instructionFromDB.setReceivedQuantity(10);
    instructionFromDB.setProjectedReceiveQty(12);
    DeliveryDocument deliveryDocument =
        gson.fromJson(instructionFromDB.getDeliveryDocument(), DeliveryDocument.class);

    deliveryDocument.getDeliveryDocumentLines().get(0).setOverageQtyLimit(2);

    instructionFromDB.setDeliveryDocument(gson.toJson(deliveryDocument));

    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instructionFromDB);
    Map<String, Object> caseLabelsInfo =
        gson.fromJson(
            MockCCMessageData.getMockFileJson(
                "../receiving-test/src/main/resources/" + "json/CCChildContainerLabels_1.json"),
            Map.class);
    when(instructionPersisterService.getPrintlabeldata(any(), anyInt(), anyInt(), any()))
        .thenReturn(caseLabelsInfo);

    container =
        new GsonBuilder()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter())
            .create()
            .fromJson(
                MockCCMessageData.getMockFileJson(
                    "../receiving-test/src/main/resources/" + "json/CCContainer.json"),
                Container.class);
    HashMap<String, Object> map = new HashMap<>();
    map.put("instruction", instructionFromDB);
    map.put("container", container);

    when(containerService.containerComplete(anyString(), anyString())).thenReturn(container);
    when(containerService.getContainerIncludingChild(any())).thenReturn(container);
    when(configUtils.getConfiguredInstance(
            "32899", ReceivingConstants.DC_FIN_SERVICE, DCFinService.class))
        .thenReturn(dcFinService);
    when(instructionHelperService.receiveInstructionAndCompleteProblemTag(
            any(), any(), anyInt(), any(), anyBoolean(), any()))
        .thenReturn(map);

    doReturn(10L).when(receiptService).getReceivedQtyByProblemId(any());
    doReturn(10L)
        .when(receiptService)
        .getReceivedQtyByPoAndPoLine(
            instructionFromDB.getPurchaseReferenceNumber(),
            instructionFromDB.getPurchaseReferenceLineNumber());

    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.MOVE_DEST_BU_ENABLED))
        .thenReturn(false);
    InstructionResponseImplNew response =
        (InstructionResponseImplNew)
            ccReceiveInstructionHandler.receiveInstruction(
                515355L, receiveInstructionRequest, httpHeaders);
    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getInstruction());
    Assert.assertNotNull(response.getPrintJob());
    Assert.assertEquals(
        ((List<Object>) response.getPrintJob().get(ReceivingConstants.PRINT_REQUEST_KEY)).size(),
        2);
  }

  @Test
  public void test_ReceiveInstruction_HappyFlow_EnableDAConStoreLabelSorterDivert()
      throws ReceivingException {
    Gson containerGson =
        new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instructionFromDB);
    Map<String, Object> caseLabelsInfo =
        gson.fromJson(
            MockCCMessageData.getMockFileJson(
                "../receiving-test/src/main/resources/" + "json/CCChildContainerLabels_1.json"),
            Map.class);
    when(instructionPersisterService.getPrintlabeldata(any(), anyInt(), anyInt(), any()))
        .thenReturn(caseLabelsInfo);
    container =
        containerGson.fromJson(
            MockCCMessageData.getMockFileJson(
                "../receiving-test/src/main/resources/" + "json/CCContainer.json"),
            Container.class);

    // parent container activity name is DACon
    container.setActivityName(ReceivingConstants.DA_CON_ACTIVITY_NAME);
    container.setPublishTs(new Date());
    container.getChildContainers().forEach(childContainer -> childContainer.setPublishTs(null));

    when(containerService.containerComplete(anyString(), anyString())).thenReturn(container);
    when(containerService.getContainerIncludingChild(any())).thenReturn(container);
    when(configUtils.getConfiguredInstance(
            "32899", ReceivingConstants.DC_FIN_SERVICE, DCFinService.class))
        .thenReturn(dcFinService);

    HashMap<String, Object> map = new HashMap<>();
    map.put("instruction", instructionFromDB);
    map.put("container", container);
    when(instructionHelperService.receiveInstructionAndCompleteProblemTag(
            any(), any(), anyInt(), any(), anyBoolean(), any()))
        .thenReturn(map);
    doReturn(10L).when(receiptService).getReceivedQtyByProblemId(any());
    doReturn(10L)
        .when(receiptService)
        .getReceivedQtyByPoAndPoLine(
            instructionFromDB.getPurchaseReferenceNumber(),
            instructionFromDB.getPurchaseReferenceLineNumber());

    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.MOVE_DEST_BU_ENABLED))
        .thenReturn(false);
    when(configUtils.isFeatureFlagEnabled(
            ReceivingConstants.ENABLE_OFFLINE_DACON_STORE_LABEL_SORTER_DIVERT))
        .thenReturn(true);
    when(configUtils.getConfiguredInstance(
            "32899", ReceivingConstants.SORTER_PUBLISHER, SorterPublisher.class))
        .thenReturn(sorterPublisher);
    InstructionResponseImplNew response =
        (InstructionResponseImplNew)
            ccReceiveInstructionHandler.receiveInstruction(
                515355L, receiveInstructionRequest, httpHeaders);

    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getInstruction());
    Assert.assertNotNull(response.getPrintJob());
    Assert.assertEquals(
        ((List<Object>) response.getPrintJob().get(ReceivingConstants.PRINT_REQUEST_KEY)).size(),
        2);

    int numChildContainers = container.getChildContainers().size();
    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(sorterPublisher, times(numChildContainers))
        .publishStoreLabel(containerArgumentCaptor.capture());
    // Assert that all child containers sent to publish method have publishTs populated
    containerArgumentCaptor
        .getAllValues()
        .forEach(childContainer -> Assert.assertNotNull(childContainer.getPublishTs()));
  }

  @Test
  public void disable_Configured_LabelFormat_True() throws ReceivingException {
    Gson containerGson =
        new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instructionFromDB);
    Map<String, Object> caseLabelsInfo =
        gson.fromJson(
            MockCCMessageData.getMockFileJson(
                "../receiving-test/src/main/resources/" + "json/CCChildContainerLabels_1.json"),
            Map.class);
    when(instructionPersisterService.getPrintlabeldata(any(), anyInt(), anyInt(), any()))
        .thenReturn(caseLabelsInfo);
    container =
        containerGson.fromJson(
            MockCCMessageData.getMockFileJson(
                "../receiving-test/src/main/resources/" + "json/CCContainer.json"),
            Container.class);

    // parent container activity name is DACon
    container.setActivityName(ReceivingConstants.DA_CON_ACTIVITY_NAME);
    container.setPublishTs(new Date());
    container.getChildContainers().forEach(childContainer -> childContainer.setPublishTs(null));

    when(containerService.containerComplete(anyString(), anyString())).thenReturn(container);
    when(containerService.getContainerIncludingChild(any())).thenReturn(container);
    when(configUtils.getConfiguredInstance(
            "32899", ReceivingConstants.DC_FIN_SERVICE, DCFinService.class))
        .thenReturn(dcFinService);
    HashMap<String, Object> map = new HashMap<>();
    map.put("instruction", instructionFromDB);
    map.put("container", container);
    when(instructionHelperService.receiveInstructionAndCompleteProblemTag(
            any(), any(), anyInt(), any(), anyBoolean(), any()))
        .thenReturn(map);
    doReturn(10L).when(receiptService).getReceivedQtyByProblemId(any());
    doReturn(10L)
        .when(receiptService)
        .getReceivedQtyByPoAndPoLine(
            instructionFromDB.getPurchaseReferenceNumber(),
            instructionFromDB.getPurchaseReferenceLineNumber());
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.MOVE_DEST_BU_ENABLED))
        .thenReturn(false);
    when(configUtils.isFeatureFlagEnabled(
            ReceivingConstants.ENABLE_OFFLINE_DACON_STORE_LABEL_SORTER_DIVERT))
        .thenReturn(true);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.DISABLE_PRINTING_MASTER_PALLET_LPN))
        .thenReturn(true);
    when(configUtils.getCcmValue(
            anyInt(), eq(ReceivingConstants.PRINT_DISABLED_LABEL_FORMAT), anyString()))
        .thenReturn("pallet_lpn_format");
    when(configUtils.getConfiguredInstance(
            "32899", ReceivingConstants.SORTER_PUBLISHER, SorterPublisher.class))
        .thenReturn(sorterPublisher);
    InstructionResponseImplNew response =
        (InstructionResponseImplNew)
            ccReceiveInstructionHandler.receiveInstruction(
                515355L, receiveInstructionRequest, httpHeaders);

    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getInstruction());
    Assert.assertNotNull(response.getPrintJob());
    Assert.assertEquals(
        ((List<Object>) response.getPrintJob().get(ReceivingConstants.PRINT_REQUEST_KEY)).size(),
        1);
  }

  @Test
  public void disable_Configured_LabelFormat_False() throws ReceivingException {
    Gson containerGson =
        new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instructionFromDB);
    Map<String, Object> caseLabelsInfo =
        gson.fromJson(
            MockCCMessageData.getMockFileJson(
                "../receiving-test/src/main/resources/" + "json/CCChildContainerLabels_1.json"),
            Map.class);
    when(instructionPersisterService.getPrintlabeldata(any(), anyInt(), anyInt(), any()))
        .thenReturn(caseLabelsInfo);
    container =
        containerGson.fromJson(
            MockCCMessageData.getMockFileJson(
                "../receiving-test/src/main/resources/" + "json/CCContainer.json"),
            Container.class);

    // parent container activity name is DACon
    container.setActivityName(ReceivingConstants.DA_CON_ACTIVITY_NAME);
    container.setPublishTs(new Date());
    container.getChildContainers().forEach(childContainer -> childContainer.setPublishTs(null));

    when(containerService.containerComplete(anyString(), anyString())).thenReturn(container);
    when(containerService.getContainerIncludingChild(any())).thenReturn(container);
    when(configUtils.getConfiguredInstance(
            "32899", ReceivingConstants.DC_FIN_SERVICE, DCFinService.class))
        .thenReturn(dcFinService);
    HashMap<String, Object> map = new HashMap<>();
    map.put("instruction", instructionFromDB);
    map.put("container", container);
    when(instructionHelperService.receiveInstructionAndCompleteProblemTag(
            any(), any(), anyInt(), any(), anyBoolean(), any()))
        .thenReturn(map);
    doReturn(10L).when(receiptService).getReceivedQtyByProblemId(any());
    doReturn(10L)
        .when(receiptService)
        .getReceivedQtyByPoAndPoLine(
            instructionFromDB.getPurchaseReferenceNumber(),
            instructionFromDB.getPurchaseReferenceLineNumber());
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.MOVE_DEST_BU_ENABLED))
        .thenReturn(false);
    when(configUtils.isFeatureFlagEnabled(
            ReceivingConstants.ENABLE_OFFLINE_DACON_STORE_LABEL_SORTER_DIVERT))
        .thenReturn(true);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.DISABLE_PRINTING_MASTER_PALLET_LPN))
        .thenReturn(false);
    when(configUtils.getCcmValue(
            anyInt(), eq(ReceivingConstants.PRINT_DISABLED_LABEL_FORMAT), anyString()))
        .thenReturn("pallet_lpn_format");
    when(configUtils.getConfiguredInstance(
            "32899", ReceivingConstants.SORTER_PUBLISHER, SorterPublisher.class))
        .thenReturn(sorterPublisher);
    InstructionResponseImplNew response =
        (InstructionResponseImplNew)
            ccReceiveInstructionHandler.receiveInstruction(
                515355L, receiveInstructionRequest, httpHeaders);
    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getInstruction());
    Assert.assertNotNull(response.getPrintJob());
    Assert.assertEquals(
        ((List<Object>) response.getPrintJob().get(ReceivingConstants.PRINT_REQUEST_KEY)).size(),
        2);
  }

  @Test
  public void printDisabledLabelFormat_notConfigured() throws ReceivingException {
    Gson containerGson =
        new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instructionFromDB);
    Map<String, Object> caseLabelsInfo =
        gson.fromJson(
            MockCCMessageData.getMockFileJson(
                "../receiving-test/src/main/resources/" + "json/CCChildContainerLabels_1.json"),
            Map.class);
    when(instructionPersisterService.getPrintlabeldata(any(), anyInt(), anyInt(), any()))
        .thenReturn(caseLabelsInfo);
    container =
        containerGson.fromJson(
            MockCCMessageData.getMockFileJson(
                "../receiving-test/src/main/resources/" + "json/CCContainer.json"),
            Container.class);

    // parent container activity name is DACon
    container.setActivityName(ReceivingConstants.DA_CON_ACTIVITY_NAME);
    container.setPublishTs(new Date());
    container.getChildContainers().forEach(childContainer -> childContainer.setPublishTs(null));

    when(containerService.containerComplete(anyString(), anyString())).thenReturn(container);
    when(containerService.getContainerIncludingChild(any())).thenReturn(container);
    when(configUtils.getConfiguredInstance(
            "32899", ReceivingConstants.DC_FIN_SERVICE, DCFinService.class))
        .thenReturn(dcFinService);
    HashMap<String, Object> map = new HashMap<>();
    map.put("instruction", instructionFromDB);
    map.put("container", container);
    when(instructionHelperService.receiveInstructionAndCompleteProblemTag(
            any(), any(), anyInt(), any(), anyBoolean(), any()))
        .thenReturn(map);
    doReturn(10L).when(receiptService).getReceivedQtyByProblemId(any());
    doReturn(10L)
        .when(receiptService)
        .getReceivedQtyByPoAndPoLine(
            instructionFromDB.getPurchaseReferenceNumber(),
            instructionFromDB.getPurchaseReferenceLineNumber());
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.MOVE_DEST_BU_ENABLED))
        .thenReturn(false);
    when(configUtils.isFeatureFlagEnabled(
            ReceivingConstants.ENABLE_OFFLINE_DACON_STORE_LABEL_SORTER_DIVERT))
        .thenReturn(true);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.DISABLE_PRINTING_MASTER_PALLET_LPN))
        .thenReturn(true);
    when(configUtils.getConfiguredInstance(
            "32899", ReceivingConstants.SORTER_PUBLISHER, SorterPublisher.class))
        .thenReturn(sorterPublisher);
    InstructionResponseImplNew response =
        (InstructionResponseImplNew)
            ccReceiveInstructionHandler.receiveInstruction(
                515355L, receiveInstructionRequest, httpHeaders);

    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getInstruction());
    Assert.assertNotNull(response.getPrintJob());
    Assert.assertEquals(
        ((List<Object>) response.getPrintJob().get(ReceivingConstants.PRINT_REQUEST_KEY)).size(),
        2);
  }
}
