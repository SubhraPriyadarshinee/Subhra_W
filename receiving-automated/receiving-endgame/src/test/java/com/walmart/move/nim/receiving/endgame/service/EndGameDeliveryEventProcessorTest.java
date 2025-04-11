package com.walmart.move.nim.receiving.endgame.service;

import static com.walmart.move.nim.receiving.endgame.common.EndGameUtilsTestCase.getLocationResponseAsJsonArray;
import static com.walmart.move.nim.receiving.endgame.common.EndGameUtilsTestCase.getReplenLocationResponseAsJsonArray;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ENABLE_AUDIT_REQUIRED_FLAG;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ENABLE_SSOT_READ;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.FLUID_REPLEN_CASE_ENABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PURCHASE_ORDER_PARTITION_SIZE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TCL_MAX_PER_DELIVERY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertNotNull;

import com.google.gson.Gson;
import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryEachesResponse;
import com.walmart.move.nim.receiving.core.model.audit.AuditFlagRequest;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.core.service.EventStoreService;
import com.walmart.move.nim.receiving.core.service.ItemMDMService;
import com.walmart.move.nim.receiving.core.service.LocationService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.endgame.common.AuditHelper;
import com.walmart.move.nim.receiving.endgame.common.DeliveryHelper;
import com.walmart.move.nim.receiving.endgame.common.EndGameUtilsTestCase;
import com.walmart.move.nim.receiving.endgame.config.EndgameManagedConfig;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.constants.LabelGenMode;
import com.walmart.move.nim.receiving.endgame.constants.LabelStatus;
import com.walmart.move.nim.receiving.endgame.model.DivertDestinationFromSlotting;
import com.walmart.move.nim.receiving.endgame.model.EndGameLabelData;
import com.walmart.move.nim.receiving.endgame.model.LabelRequestVO;
import com.walmart.move.nim.receiving.endgame.model.SlottingDivertResponse;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventStoreType;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class EndGameDeliveryEventProcessorTest extends ReceivingTestBase {

  private DivertDestinationFromSlotting destination;
  private SlottingDivertResponse slottingDivertResponse;
  private ReceiptSummaryEachesResponse receivedQty;
  private EndGameLabelData endGameLabelData;
  private DeliveryMetaData deliveryMetaData;
  private Map<String, Boolean> auditFlagResponseMap;

  @InjectMocks private EndGameDeliveryEventProcessor endgameDeliveryEventProcessor;

  @Mock private EndGameDeliveryService endGameDeliveryService;
  @Mock private EndGameLabelingService labelingService;
  @Mock private EndGameSlottingService slottingService;
  @Mock private ReceiptService receiptService;
  @Mock private ItemMDMService itemMDMService;
  @Mock private AuditHelper auditHelper;
  @Mock private EndGameDeliveryMetaDataService endGameDeliveryMetaDataService;
  @Mock private EndgameManagedConfig endgameManagedConfig;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private DeliveryHelper deliveryHelper;
  @MockBean private IOutboxPublisherService iOutboxPublisherService;
  @Mock private EventStoreService eventStoreService;
  @Mock private LocationService locationService;
  @Mock private RetryableRestConnector retryableRestConnector;

  private Map<String, String> slotingAttributes = null;

  private Gson gson;
  @Mock private RestTemplate restTemplate;
  private HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
  private HttpEntity<String> entity = new HttpEntity<String>(httpHeaders);

  @BeforeClass
  private void initMocks() {
    MockitoAnnotations.initMocks(this);

    gson = new Gson();
    slotingAttributes = new HashMap<>();
    slotingAttributes.put(EndgameConstants.ATTRIBUTES_FTS, "false");

    slottingDivertResponse = new SlottingDivertResponse();
    slottingDivertResponse.setMessageId(UUID.randomUUID().toString());
    destination = new DivertDestinationFromSlotting();
    destination.setCaseUPC("00028000113002");
    destination.setSellerId("F55CDC31AB754BB68FE0B39041159D63");
    destination.setDivertLocation("DECANT");
    destination.setItemNbr(9213971L);
    destination.setAttributes(slotingAttributes);
    slottingDivertResponse.setDivertLocations(Collections.singletonList(destination));
    AppConfig appConfig = new AppConfig();
    EndgameManagedConfig endgameManagedConfig = new EndgameManagedConfig();
    ReflectionTestUtils.setField(endgameManagedConfig, "isNewItemPath", "$.dcProperties.isNewItem");
    endGameLabelData =
        EndGameLabelData.builder()
            .defaultTCL("TD" + EndGameUtilsTestCase.getDelivery().getDeliveryNumber())
            .trailerCaseLabels(Collections.singleton("TA00000001"))
            .doorNumber("123")
            .clientId(EndgameConstants.CLIENT_ID)
            .formatName(EndgameConstants.TCL_LABEL_FORMAT_NAME)
            .deliveryNumber(EndGameUtilsTestCase.getDelivery().getDeliveryNumber().toString())
            .trailer(
                EndGameUtilsTestCase.getDelivery()
                    .getLoadInformation()
                    .getTrailerInformation()
                    .getTrailerId())
            .user(EndgameConstants.DEFAULT_USER)
            .tclTemplate("")
            .labelGenMode(LabelGenMode.AUTOMATED.getMode())
            .build();

    deliveryMetaData = new DeliveryMetaData();
    deliveryMetaData.setDeliveryNumber(
        EndGameUtilsTestCase.getDelivery().getDeliveryNumber().toString());
    deliveryMetaData.setTotalCaseLabelSent(0);
    auditFlagResponseMap = new HashMap<>();
    auditFlagResponseMap.put("8-9213971L", true);
    when(configUtils.getTCLMaxValPerDelivery(anyString())).thenReturn(TCL_MAX_PER_DELIVERY);
    ReflectionTestUtils.setField(endgameManagedConfig, "isNewItemPath", "$.dcProperties.isNewItem");
    ReflectionTestUtils.setField(endgameDeliveryEventProcessor, "gson", gson);
    ReflectionTestUtils.setField(endgameDeliveryEventProcessor, "deliveryHelper", deliveryHelper);
    TenantContext.setFacilityNum(54321);
    TenantContext.setFacilityCountryCode("us");
    receivedQty = new ReceiptSummaryEachesResponse("5764031011", 1, null, 5L);
  }

  @AfterMethod
  public void resetMocks() {
    reset(endGameDeliveryService);
    reset(labelingService);
    reset(slottingService);
    reset(itemMDMService);
    reset(receiptService);
    reset(endGameDeliveryMetaDataService);
    reset(auditHelper);
    reset(deliveryHelper);
    reset(endgameManagedConfig);
    reset(restTemplate);
  }

  @Test
  public void testHappyPath() throws ReceivingException {
    DeliveryUpdateMessage deliveryUpdateMessage = EndGameUtilsTestCase.getDeliveryUpdateMessage();
    deliveryUpdateMessage.setEventType("DOOR_ASSIGNED");
    deliveryUpdateMessage.setDeliveryStatus("ARV");
    when(configUtils.getConfiguredFeatureFlag(eq(ENABLE_AUDIT_REQUIRED_FLAG)))
        .thenReturn(Boolean.TRUE);
    when(endgameManagedConfig.getWalmartDefaultSellerId())
        .thenReturn("F55CDC31AB754BB68FE0B39041159D63");
    when(endgameManagedConfig.getSamsDefaultSellerId()).thenReturn("0");
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    when(endGameDeliveryService.getGDMData(any())).thenReturn(EndGameUtilsTestCase.getDelivery());
    when(slottingService.getDivertsFromSlotting(any())).thenReturn(slottingDivertResponse);
    when(labelingService.generateLabel(any(LabelRequestVO.class))).thenReturn(endGameLabelData);
    when(configUtils.getPurchaseOrderPartitionSize(anyString()))
        .thenReturn(PURCHASE_ORDER_PARTITION_SIZE);
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
        .thenReturn(Collections.singletonList(receivedQty));
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(getItemDetails(EndGameUtilsTestCase.getDelivery()));
    doNothing().when(labelingService).persistLabel(any(EndGameLabelData.class));
    when(labelingService.send(any(EndGameLabelData.class)))
        .thenReturn(EndgameConstants.SUCCESS_MSG);
    doNothing()
        .when(labelingService)
        .updateStatus(any(LabelStatus.class), any(LabelStatus.class), anyString(), anyLong());
    when(auditHelper.fetchAndSaveAuditInfo(any(AuditFlagRequest.class)))
        .thenReturn(auditFlagResponseMap);
    endgameDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
  }

  @Test
  public void testPoUpdate() throws ReceivingException {
    DeliveryUpdateMessage deliveryUpdateMessage = EndGameUtilsTestCase.getDeliveryUpdateMessage();
    deliveryUpdateMessage.setEventType("PO_ADDED");
    deliveryUpdateMessage.setDeliveryStatus("ARV");
    when(configUtils.getConfiguredFeatureFlag(eq(ENABLE_AUDIT_REQUIRED_FLAG)))
        .thenReturn(Boolean.TRUE);
    when(configUtils.getPurchaseOrderPartitionSize(anyString()))
        .thenReturn(PURCHASE_ORDER_PARTITION_SIZE);
    when(endgameManagedConfig.getWalmartDefaultSellerId())
        .thenReturn("F55CDC31AB754BB68FE0B39041159D63");
    when(endgameManagedConfig.getSamsDefaultSellerId()).thenReturn("0");
    when(endGameDeliveryService.getGDMData(any())).thenReturn(EndGameUtilsTestCase.getDelivery());
    when(labelingService.generateLabel(any(LabelRequestVO.class))).thenReturn(endGameLabelData);
    when(slottingService.getDivertsFromSlotting(any())).thenReturn(slottingDivertResponse);
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
        .thenReturn(Collections.singletonList(receivedQty));
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(getItemDetails(EndGameUtilsTestCase.getDelivery()));
    doNothing().when(labelingService).persistLabel(any(EndGameLabelData.class));
    when(labelingService.send(any(EndGameLabelData.class)))
        .thenReturn(EndgameConstants.SUCCESS_MSG);
    doNothing()
        .when(labelingService)
        .updateStatus(any(LabelStatus.class), any(LabelStatus.class), anyString(), anyLong());
    when(auditHelper.fetchAndSaveAuditInfo(any(AuditFlagRequest.class)))
        .thenReturn(auditFlagResponseMap);
    endgameDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
  }

  @Test
  public void testPoUpdateInScheduledDelivery() throws ReceivingException {
    DeliveryUpdateMessage deliveryUpdateMessage = EndGameUtilsTestCase.getDeliveryUpdateMessage();
    deliveryUpdateMessage.setEventType("PO_ADDED");
    deliveryUpdateMessage.setDeliveryStatus("SCH");
    endgameDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
    verify(labelingService, times(0)).generateLabel(any(LabelRequestVO.class));
    verify(slottingService, times(0)).getDivertsFromSlotting(any());
  }

  private Map<String, Object> getItemDetails(Delivery delivery) {
    Map<String, Object> itemAttrMap = new HashMap<>();
    List<Map<String, Object>> list = new ArrayList();
    assertNotNull(EndGameUtilsTestCase.getItemDetails());
    list.add(EndGameUtilsTestCase.getItemDetails());
    itemAttrMap.put(EndgameConstants.ITEM_FOUND_SUPPLY_ITEM, list);
    return itemAttrMap;
  }

  private Map<String, Object> getSSOTItemDetails(String path) {
    Map<String, Object> itemAttrMap = new HashMap<>();
    List<Map<String, Object>> list = new ArrayList();
    assertNotNull(EndGameUtilsTestCase.getSSOTItemDetails(path));
    list.add(EndGameUtilsTestCase.getSSOTItemDetails(path));
    itemAttrMap.put(EndgameConstants.ITEM_FOUND_SUPPLY_ITEM, list);
    return itemAttrMap;
  }

  @Test
  public void testDifferentDeliveryEventTypeMessage() throws ReceivingException {
    DeliveryUpdateMessage deliveryUpdateMessage = EndGameUtilsTestCase.getDeliveryUpdateMessage();
    deliveryUpdateMessage.setEventType("ARRIVED");
    deliveryUpdateMessage.setDeliveryStatus("ARV");
    endgameDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
  }

  @Test
  public void testAuditFailure() throws ReceivingException {
    DeliveryUpdateMessage deliveryUpdateMessage = EndGameUtilsTestCase.getDeliveryUpdateMessage();
    deliveryUpdateMessage.setEventType("DOOR_ASSIGNED");
    deliveryUpdateMessage.setDeliveryStatus("ARV");
    when(configUtils.getConfiguredFeatureFlag(eq(ENABLE_AUDIT_REQUIRED_FLAG)))
        .thenReturn(Boolean.TRUE);
    when(configUtils.getPurchaseOrderPartitionSize(anyString()))
        .thenReturn(PURCHASE_ORDER_PARTITION_SIZE);
    when(endgameManagedConfig.getWalmartDefaultSellerId())
        .thenReturn("F55CDC31AB754BB68FE0B39041159D63");
    when(endgameManagedConfig.getSamsDefaultSellerId()).thenReturn("0");
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    when(endGameDeliveryService.getGDMData(any())).thenReturn(EndGameUtilsTestCase.getDelivery());
    when(slottingService.getDivertsFromSlotting(any())).thenReturn(slottingDivertResponse);
    when(labelingService.generateLabel(any(LabelRequestVO.class))).thenReturn(endGameLabelData);
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
        .thenReturn(Collections.singletonList(receivedQty));
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(getItemDetails(EndGameUtilsTestCase.getDelivery()));
    doNothing().when(labelingService).persistLabel(any(EndGameLabelData.class));
    when(labelingService.send(any(EndGameLabelData.class)))
        .thenReturn(EndgameConstants.SUCCESS_MSG);
    doNothing()
        .when(labelingService)
        .updateStatus(any(LabelStatus.class), any(LabelStatus.class), anyString(), anyLong());
    when(auditHelper.fetchAndSaveAuditInfo(any(AuditFlagRequest.class)))
        .thenReturn(new HashMap<>());
    endgameDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
  }

  @Test
  public void testProcessEventForEaches() throws ReceivingException {
    DeliveryUpdateMessage deliveryUpdateMessage = EndGameUtilsTestCase.getDeliveryUpdateMessage();
    deliveryUpdateMessage.setEventType("DOOR_ASSIGNED");
    deliveryUpdateMessage.setDeliveryStatus("ARV");
    when(configUtils.getConfiguredFeatureFlag(eq(ENABLE_AUDIT_REQUIRED_FLAG)))
        .thenReturn(Boolean.TRUE);
    when(configUtils.getPurchaseOrderPartitionSize(anyString()))
        .thenReturn(PURCHASE_ORDER_PARTITION_SIZE);
    when(endgameManagedConfig.getWalmartDefaultSellerId())
        .thenReturn("F55CDC31AB754BB68FE0B39041159D63");
    when(endgameManagedConfig.getSamsDefaultSellerId()).thenReturn("0");
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    when(endGameDeliveryService.getGDMData(any()))
        .thenReturn(EndGameUtilsTestCase.getDeliveryForEaches());
    when(slottingService.getDivertsFromSlotting(any())).thenReturn(slottingDivertResponse);
    when(labelingService.generateLabel(any(LabelRequestVO.class))).thenReturn(endGameLabelData);
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
        .thenReturn(Collections.singletonList(receivedQty));
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(getItemDetails(EndGameUtilsTestCase.getDelivery()));
    doNothing().when(labelingService).persistLabel(any(EndGameLabelData.class));
    when(labelingService.send(any(EndGameLabelData.class)))
        .thenReturn(EndgameConstants.SUCCESS_MSG);
    doNothing()
        .when(labelingService)
        .updateStatus(any(LabelStatus.class), any(LabelStatus.class), anyString(), anyLong());
    when(auditHelper.fetchAndSaveAuditInfo(any(AuditFlagRequest.class)))
        .thenReturn(auditFlagResponseMap);
    endgameDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
    verify(auditHelper, times(1)).fetchAndSaveAuditInfo(any());
  }

  @Test
  public void testHappyPathForWhpk() throws ReceivingException {
    DeliveryUpdateMessage deliveryUpdateMessage = EndGameUtilsTestCase.getDeliveryUpdateMessage();
    deliveryUpdateMessage.setEventType("DOOR_ASSIGNED");
    deliveryUpdateMessage.setDeliveryStatus("ARV");
    when(configUtils.getConfiguredFeatureFlag(eq(ENABLE_AUDIT_REQUIRED_FLAG)))
        .thenReturn(Boolean.TRUE);
    when(configUtils.getPurchaseOrderPartitionSize(anyString()))
        .thenReturn(PURCHASE_ORDER_PARTITION_SIZE);
    when(endgameManagedConfig.getWalmartDefaultSellerId())
        .thenReturn("F55CDC31AB754BB68FE0B39041159D63");
    when(endgameManagedConfig.getSamsDefaultSellerId()).thenReturn("0");
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    when(endGameDeliveryService.getGDMData(any()))
        .thenReturn(EndGameUtilsTestCase.getDeliveryForWhpk());
    when(slottingService.getDivertsFromSlotting(any())).thenReturn(slottingDivertResponse);
    when(labelingService.generateLabel(any(LabelRequestVO.class))).thenReturn(endGameLabelData);
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
        .thenReturn(Collections.singletonList(receivedQty));
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(getItemDetails(EndGameUtilsTestCase.getDelivery()));
    doNothing().when(labelingService).persistLabel(any(EndGameLabelData.class));
    when(labelingService.send(any(EndGameLabelData.class)))
        .thenReturn(EndgameConstants.SUCCESS_MSG);
    doNothing()
        .when(labelingService)
        .updateStatus(any(LabelStatus.class), any(LabelStatus.class), anyString(), anyLong());
    when(auditHelper.fetchAndSaveAuditInfo(any(AuditFlagRequest.class)))
        .thenReturn(auditFlagResponseMap);
    endgameDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
    verify(auditHelper, times(1)).fetchAndSaveAuditInfo(any());
  }

  @Test
  public void testSSOTHappyPath() throws ReceivingException {
    DeliveryUpdateMessage deliveryUpdateMessage = EndGameUtilsTestCase.getDeliveryUpdateMessage();
    deliveryUpdateMessage.setEventType("DOOR_ASSIGNED");
    deliveryUpdateMessage.setDeliveryStatus("ARV");
    when(configUtils.getConfiguredFeatureFlag(eq(ENABLE_AUDIT_REQUIRED_FLAG)))
        .thenReturn(Boolean.TRUE);
    when(configUtils.getConfiguredFeatureFlag(eq(ENABLE_SSOT_READ))).thenReturn(Boolean.TRUE);
    when(configUtils.getPurchaseOrderPartitionSize(anyString()))
        .thenReturn(PURCHASE_ORDER_PARTITION_SIZE);
    when(endgameManagedConfig.getWalmartDefaultSellerId())
        .thenReturn("F55CDC31AB754BB68FE0B39041159D63");
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    when(endGameDeliveryService.getGDMData(any())).thenReturn(EndGameUtilsTestCase.getDelivery());
    when(slottingService.getDivertsFromSlotting(any())).thenReturn(slottingDivertResponse);
    when(labelingService.generateLabel(any(LabelRequestVO.class))).thenReturn(endGameLabelData);
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
        .thenReturn(Collections.singletonList(receivedQty));
    String path = "../../receiving-test/src/main/resources/json/ssot_item_details_9213971.json";
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(getSSOTItemDetails(path));
    doNothing().when(labelingService).persistLabel(any(EndGameLabelData.class));
    when(labelingService.send(any(EndGameLabelData.class)))
        .thenReturn(EndgameConstants.SUCCESS_MSG);
    doNothing()
        .when(labelingService)
        .updateStatus(any(LabelStatus.class), any(LabelStatus.class), anyString(), anyLong());
    when(auditHelper.fetchAndSaveAuditInfo(any(AuditFlagRequest.class)))
        .thenReturn(auditFlagResponseMap);
    when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), eq(entity), eq(String.class)))
        .thenReturn(new ResponseEntity<>("", HttpStatus.OK));
    endgameDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
  }

  @Test
  public void testSSOTFeatureFlagOff() throws ReceivingException {
    DeliveryUpdateMessage deliveryUpdateMessage = EndGameUtilsTestCase.getDeliveryUpdateMessage();
    deliveryUpdateMessage.setEventType("DOOR_ASSIGNED");
    deliveryUpdateMessage.setDeliveryStatus("ARV");
    when(configUtils.getConfiguredFeatureFlag(eq(ENABLE_AUDIT_REQUIRED_FLAG)))
        .thenReturn(Boolean.TRUE);
    when(configUtils.getConfiguredFeatureFlag(eq(ENABLE_SSOT_READ))).thenReturn(Boolean.FALSE);
    when(configUtils.getPurchaseOrderPartitionSize(anyString()))
        .thenReturn(PURCHASE_ORDER_PARTITION_SIZE);
    when(endgameManagedConfig.getWalmartDefaultSellerId())
        .thenReturn("F55CDC31AB754BB68FE0B39041159D63");
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    when(endGameDeliveryService.getGDMData(any())).thenReturn(EndGameUtilsTestCase.getDelivery());
    when(slottingService.getDivertsFromSlotting(any())).thenReturn(slottingDivertResponse);
    when(labelingService.generateLabel(any(LabelRequestVO.class))).thenReturn(endGameLabelData);
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
        .thenReturn(Collections.singletonList(receivedQty));
    String path = "../../receiving-test/src/main/resources/json/ssot_item_details_9213971.json";
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(getSSOTItemDetails(path));
    doNothing().when(labelingService).persistLabel(any(EndGameLabelData.class));
    when(labelingService.send(any(EndGameLabelData.class)))
        .thenReturn(EndgameConstants.SUCCESS_MSG);
    doNothing()
        .when(labelingService)
        .updateStatus(any(LabelStatus.class), any(LabelStatus.class), anyString(), anyLong());
    when(auditHelper.fetchAndSaveAuditInfo(any(AuditFlagRequest.class)))
        .thenReturn(auditFlagResponseMap);
    endgameDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
  }

  @Test
  public void testSSOTDCPropertiesNull() throws ReceivingException {
    DeliveryUpdateMessage deliveryUpdateMessage = EndGameUtilsTestCase.getDeliveryUpdateMessage();
    deliveryUpdateMessage.setEventType("DOOR_ASSIGNED");
    deliveryUpdateMessage.setDeliveryStatus("ARV");
    when(configUtils.getConfiguredFeatureFlag(eq(ENABLE_AUDIT_REQUIRED_FLAG)))
        .thenReturn(Boolean.TRUE);
    when(configUtils.getConfiguredFeatureFlag(eq(ENABLE_SSOT_READ))).thenReturn(Boolean.FALSE);
    when(configUtils.getPurchaseOrderPartitionSize(anyString()))
        .thenReturn(PURCHASE_ORDER_PARTITION_SIZE);
    when(endgameManagedConfig.getWalmartDefaultSellerId())
        .thenReturn("F55CDC31AB754BB68FE0B39041159D63");
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    when(endGameDeliveryService.getGDMData(any())).thenReturn(EndGameUtilsTestCase.getDelivery());
    when(slottingService.getDivertsFromSlotting(any())).thenReturn(slottingDivertResponse);
    when(labelingService.generateLabel(any(LabelRequestVO.class))).thenReturn(endGameLabelData);
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
        .thenReturn(Collections.singletonList(receivedQty));
    String path =
        "../../receiving-test/src/main/resources/json/ssot_item_details_9213971_DC_Properties_Empty.json";
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(getSSOTItemDetails(path));
    doNothing().when(labelingService).persistLabel(any(EndGameLabelData.class));
    when(labelingService.send(any(EndGameLabelData.class)))
        .thenReturn(EndgameConstants.SUCCESS_MSG);
    doNothing()
        .when(labelingService)
        .updateStatus(any(LabelStatus.class), any(LabelStatus.class), anyString(), anyLong());
    when(auditHelper.fetchAndSaveAuditInfo(any(AuditFlagRequest.class)))
        .thenReturn(auditFlagResponseMap);
    endgameDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
  }

  @Test
  public void testSSOTIsNewItemNull() throws ReceivingException {
    DeliveryUpdateMessage deliveryUpdateMessage = EndGameUtilsTestCase.getDeliveryUpdateMessage();
    deliveryUpdateMessage.setEventType("DOOR_ASSIGNED");
    deliveryUpdateMessage.setDeliveryStatus("ARV");
    when(configUtils.getConfiguredFeatureFlag(eq(ENABLE_AUDIT_REQUIRED_FLAG)))
        .thenReturn(Boolean.TRUE);
    when(configUtils.getConfiguredFeatureFlag(eq(ENABLE_SSOT_READ))).thenReturn(Boolean.FALSE);
    when(configUtils.getPurchaseOrderPartitionSize(anyString()))
        .thenReturn(PURCHASE_ORDER_PARTITION_SIZE);
    when(endgameManagedConfig.getWalmartDefaultSellerId())
        .thenReturn("F55CDC31AB754BB68FE0B39041159D63");
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    when(endGameDeliveryService.getGDMData(any())).thenReturn(EndGameUtilsTestCase.getDelivery());
    when(slottingService.getDivertsFromSlotting(any())).thenReturn(slottingDivertResponse);
    when(labelingService.generateLabel(any(LabelRequestVO.class))).thenReturn(endGameLabelData);
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
        .thenReturn(Collections.singletonList(receivedQty));
    String path =
        "../../receiving-test/src/main/resources/json/ssot_item_details_9213971_IsNew_Item_Null.json";
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(getSSOTItemDetails(path));
    doNothing().when(labelingService).persistLabel(any(EndGameLabelData.class));
    when(labelingService.send(any(EndGameLabelData.class)))
        .thenReturn(EndgameConstants.SUCCESS_MSG);
    doNothing()
        .when(labelingService)
        .updateStatus(any(LabelStatus.class), any(LabelStatus.class), anyString(), anyLong());
    when(auditHelper.fetchAndSaveAuditInfo(any(AuditFlagRequest.class)))
        .thenReturn(auditFlagResponseMap);
    endgameDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
  }

  @Test
  public void testSSOTOfferNull() throws ReceivingException {
    DeliveryUpdateMessage deliveryUpdateMessage = EndGameUtilsTestCase.getDeliveryUpdateMessage();
    deliveryUpdateMessage.setEventType("DOOR_ASSIGNED");
    deliveryUpdateMessage.setDeliveryStatus("ARV");
    when(configUtils.getConfiguredFeatureFlag(eq(ENABLE_AUDIT_REQUIRED_FLAG)))
        .thenReturn(Boolean.TRUE);
    when(configUtils.getConfiguredFeatureFlag(eq(ENABLE_SSOT_READ))).thenReturn(Boolean.TRUE);
    when(configUtils.getPurchaseOrderPartitionSize(anyString()))
        .thenReturn(PURCHASE_ORDER_PARTITION_SIZE);
    when(endgameManagedConfig.getWalmartDefaultSellerId())
        .thenReturn("F55CDC31AB754BB68FE0B39041159D63");
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    when(endGameDeliveryService.getGDMData(any())).thenReturn(EndGameUtilsTestCase.getDelivery());
    when(slottingService.getDivertsFromSlotting(any())).thenReturn(slottingDivertResponse);
    when(labelingService.generateLabel(any(LabelRequestVO.class))).thenReturn(endGameLabelData);
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
        .thenReturn(Collections.singletonList(receivedQty));
    String path =
        "../../receiving-test/src/main/resources/json/ssot_item_details_9213971_Offer_Empty.json";
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(getSSOTItemDetails(path));
    doNothing().when(labelingService).persistLabel(any(EndGameLabelData.class));
    when(labelingService.send(any(EndGameLabelData.class)))
        .thenReturn(EndgameConstants.SUCCESS_MSG);
    doNothing()
        .when(labelingService)
        .updateStatus(any(LabelStatus.class), any(LabelStatus.class), anyString(), anyLong());
    when(auditHelper.fetchAndSaveAuditInfo(any(AuditFlagRequest.class)))
        .thenReturn(auditFlagResponseMap);
    when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), eq(entity), eq(String.class)))
        .thenReturn(new ResponseEntity<>("", HttpStatus.OK));
    endgameDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
  }

  @Test
  public void testSSOTSellerIDNull() throws ReceivingException {
    DeliveryUpdateMessage deliveryUpdateMessage = EndGameUtilsTestCase.getDeliveryUpdateMessage();
    deliveryUpdateMessage.setEventType("DOOR_ASSIGNED");
    deliveryUpdateMessage.setDeliveryStatus("ARV");
    when(configUtils.getConfiguredFeatureFlag(eq(ENABLE_AUDIT_REQUIRED_FLAG)))
        .thenReturn(Boolean.TRUE);
    when(configUtils.getConfiguredFeatureFlag(eq(ENABLE_SSOT_READ))).thenReturn(Boolean.TRUE);
    when(endgameManagedConfig.getWalmartDefaultSellerId())
        .thenReturn("F55CDC31AB754BB68FE0B39041159D63");
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    when(endGameDeliveryService.getGDMData(any())).thenReturn(EndGameUtilsTestCase.getDelivery());
    when(slottingService.getDivertsFromSlotting(any())).thenReturn(slottingDivertResponse);
    when(labelingService.generateLabel(any(LabelRequestVO.class))).thenReturn(endGameLabelData);
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
        .thenReturn(Collections.singletonList(receivedQty));
    String path =
        "../../receiving-test/src/main/resources/json/ssot_item_details_9213971_SellerId_Null.json";
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(getSSOTItemDetails(path));
    doNothing().when(labelingService).persistLabel(any(EndGameLabelData.class));
    when(labelingService.send(any(EndGameLabelData.class)))
        .thenReturn(EndgameConstants.SUCCESS_MSG);
    doNothing()
        .when(labelingService)
        .updateStatus(any(LabelStatus.class), any(LabelStatus.class), anyString(), anyLong());
    when(auditHelper.fetchAndSaveAuditInfo(any(AuditFlagRequest.class)))
        .thenReturn(auditFlagResponseMap);
    when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), eq(entity), eq(String.class)))
        .thenReturn(new ResponseEntity<>("", HttpStatus.OK));
    endgameDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
  }

  @Test
  public void testProcessEventForNonReplenCase() throws ReceivingException {
    DeliveryUpdateMessage deliveryUpdateMessage = EndGameUtilsTestCase.getDeliveryUpdateMessage();
    deliveryUpdateMessage.setEventType("DOOR_ASSIGNED");
    deliveryUpdateMessage.setDeliveryStatus("ARV");
    when(configUtils.getConfiguredFeatureFlag(FLUID_REPLEN_CASE_ENABLED)).thenReturn(Boolean.TRUE);
    when(configUtils.getConfiguredFeatureFlag(ENABLE_AUDIT_REQUIRED_FLAG)).thenReturn(Boolean.TRUE);
    when(configUtils.getPurchaseOrderPartitionSize(anyString()))
        .thenReturn(PURCHASE_ORDER_PARTITION_SIZE);
    when(endgameManagedConfig.getWalmartDefaultSellerId())
        .thenReturn("F55CDC31AB754BB68FE0B39041159D63");
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    when(endGameDeliveryService.getGDMData(any()))
        .thenReturn(EndGameUtilsTestCase.getDeliveryForEaches());
    when(slottingService.getDivertsFromSlotting(any())).thenReturn(slottingDivertResponse);
    when(labelingService.generateLabel(any(LabelRequestVO.class))).thenReturn(endGameLabelData);
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
        .thenReturn(Collections.singletonList(receivedQty));
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(getItemDetails(EndGameUtilsTestCase.getDelivery()));
    doNothing().when(labelingService).persistLabel(any(EndGameLabelData.class));
    when(labelingService.send(any(EndGameLabelData.class)))
        .thenReturn(EndgameConstants.SUCCESS_MSG);
    doNothing()
        .when(labelingService)
        .updateStatus(any(LabelStatus.class), any(LabelStatus.class), anyString(), anyLong());
    when(auditHelper.fetchAndSaveAuditInfo(any(AuditFlagRequest.class)))
        .thenReturn(auditFlagResponseMap);
    when(eventStoreService.getEventStoreByKeyStatusAndEventType(
            anyString(), any(EventTargetStatus.class), any(EventStoreType.class)))
        .thenReturn(Optional.empty());
    when(locationService.getLocationInfoAsJsonArray(anyString()))
        .thenReturn(getLocationResponseAsJsonArray());

    when(eventStoreService.updateEventStoreEntityStatusAndLastUpdatedDateByCriteria(
            anyString(),
            anyLong(),
            any(EventTargetStatus.class),
            any(EventStoreType.class),
            any(Date.class)))
        .thenReturn(0);
    endgameDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
    verify(auditHelper, times(1)).fetchAndSaveAuditInfo(any());
  }

  @Test
  public void testProcessEventForReplenCase() throws ReceivingException {
    DeliveryUpdateMessage deliveryUpdateMessage = EndGameUtilsTestCase.getDeliveryUpdateMessage();
    deliveryUpdateMessage.setEventType("DOOR_ASSIGNED");
    deliveryUpdateMessage.setDeliveryStatus("ARV");
    when(configUtils.getConfiguredFeatureFlag(FLUID_REPLEN_CASE_ENABLED)).thenReturn(Boolean.TRUE);
    when(configUtils.getConfiguredFeatureFlag(ENABLE_AUDIT_REQUIRED_FLAG)).thenReturn(Boolean.TRUE);
    when(configUtils.getPurchaseOrderPartitionSize(anyString()))
        .thenReturn(PURCHASE_ORDER_PARTITION_SIZE);
    when(endgameManagedConfig.getWalmartDefaultSellerId())
        .thenReturn("F55CDC31AB754BB68FE0B39041159D63");
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    when(endGameDeliveryService.getGDMData(any()))
        .thenReturn(EndGameUtilsTestCase.getDeliveryForEaches());
    when(slottingService.getDivertsFromSlotting(any())).thenReturn(slottingDivertResponse);
    when(labelingService.generateLabel(any(LabelRequestVO.class))).thenReturn(endGameLabelData);
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
        .thenReturn(Collections.singletonList(receivedQty));
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(getItemDetails(EndGameUtilsTestCase.getDelivery()));
    doNothing().when(labelingService).persistLabel(any(EndGameLabelData.class));
    when(labelingService.send(any(EndGameLabelData.class)))
        .thenReturn(EndgameConstants.SUCCESS_MSG);
    doNothing()
        .when(labelingService)
        .updateStatus(any(LabelStatus.class), any(LabelStatus.class), anyString(), anyLong());
    when(auditHelper.fetchAndSaveAuditInfo(any(AuditFlagRequest.class)))
        .thenReturn(auditFlagResponseMap);
    when(eventStoreService.getEventStoreByKeyStatusAndEventType(
            anyString(), any(EventTargetStatus.class), any(EventStoreType.class)))
        .thenReturn(Optional.empty());
    when(locationService.getLocationInfoAsJsonArray(anyString()))
        .thenReturn(getReplenLocationResponseAsJsonArray());

    when(eventStoreService.updateEventStoreEntityStatusAndLastUpdatedDateByCriteria(
            anyString(),
            anyLong(),
            any(EventTargetStatus.class),
            any(EventStoreType.class),
            any(Date.class)))
        .thenReturn(0);
    endgameDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
    verify(eventStoreService, times(1)).saveEventStoreEntity(any());
    verify(eventStoreService, times(1))
        .updateEventStoreEntityStatusAndLastUpdatedDateByKeyOrDeliveryNumber(
            anyString(),
            anyLong(),
            any(EventTargetStatus.class),
            any(EventStoreType.class),
            any(Date.class));
  }
}
