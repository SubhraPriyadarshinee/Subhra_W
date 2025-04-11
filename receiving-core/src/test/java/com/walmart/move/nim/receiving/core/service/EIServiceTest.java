package com.walmart.move.nim.receiving.core.service;

import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.message.publisher.RdcKafkaEIPublisher;
import com.walmart.move.nim.receiving.core.model.ei.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.apache.commons.lang3.ObjectUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class EIServiceTest {

  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private RdcKafkaEIPublisher rdcKafkaEIPublisher;
  @InjectMocks private EIService eiService;
  private HttpHeaders httpHeaders;
  private static final String facilityNum = "32818";
  private static final String countryCode = "US";
  private Gson gson = new Gson();

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    TenantContext.setFacilityCountryCode(countryCode);
    ReflectionTestUtils.setField(eiService, "dcReceivingTopic", "dcReceiving");
    ReflectionTestUtils.setField(eiService, "dcPicksTopic", "dcPicks");
    ReflectionTestUtils.setField(eiService, "dcVoidTopic", "dcVoid");
    ReflectionTestUtils.setField(eiService, "gson", gson);
    httpHeaders = MockHttpHeaders.getHeaders(facilityNum, countryCode);
  }

  @AfterMethod
  public void shutdownMocks() {
    reset(rdcKafkaEIPublisher, configUtils);
  }

  @Test
  public void testPublishContainerToInventoryDCReceiving() {
    Container consolidatedContainer = mockContainer();
    InventoryDetails inventoryDetails = mockInventoryDetails();
    Map<String, Object> httpHeaders = new HashMap<>();
    String kafkaKey = consolidatedContainer.getTrackingId();
    String kafkaValue = gson.toJson(inventoryDetails);
    String eiTopic = "dcReceiving";
    doNothing().when(rdcKafkaEIPublisher).publishEvent(kafkaKey, kafkaValue, eiTopic, httpHeaders);
    eiService.publishContainerToEI(
        consolidatedContainer, inventoryDetails, ReceivingConstants.DC_RECEIVING);
    verify(rdcKafkaEIPublisher, times(1)).publishEvent(kafkaKey, kafkaValue, eiTopic, httpHeaders);
  }

  @Test
  public void testPublishContainerToInventoryDCPicks() {
    Container consolidatedContainer = mockContainer();
    InventoryDetails inventoryDetails = mockInventoryDetails();
    Map<String, Object> httpHeaders = new HashMap<>();
    populateTickTickHeaders(httpHeaders, inventoryDetails, ReceivingConstants.DC_PICK_EVENT_CODE);
    String kafkaKey = consolidatedContainer.getTrackingId();
    String kafkaValue = gson.toJson(inventoryDetails);
    String eiTopic = "dcPicks";
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_TICK_TICK_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    doNothing().when(rdcKafkaEIPublisher).publishEvent(kafkaKey, kafkaValue, eiTopic, httpHeaders);
    eiService.publishContainerToEI(
        consolidatedContainer, inventoryDetails, ReceivingConstants.DC_PICKS);
    verify(rdcKafkaEIPublisher, times(1)).publishEvent(kafkaKey, kafkaValue, eiTopic, httpHeaders);
  }

  @Test
  public void testPublishContainerToInventoryDCVoid() {
    Container consolidatedContainer = mockContainer();
    InventoryDetails inventoryDetails = mockInventoryDetails();
    Map<String, Object> httpHeaders = new HashMap<>();
    populateTickTickHeaders(httpHeaders, inventoryDetails, ReceivingConstants.DC_VOIDS_EVENT_CODE);
    String kafkaKey = consolidatedContainer.getTrackingId();
    String kafkaValue = gson.toJson(inventoryDetails);
    String eiTopic = "dcVoid";
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_TICK_TICK_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);

    doNothing().when(rdcKafkaEIPublisher).publishEvent(kafkaKey, kafkaValue, eiTopic, httpHeaders);
    eiService.publishContainerToEI(
        consolidatedContainer, inventoryDetails, ReceivingConstants.DC_VOID);
    verify(rdcKafkaEIPublisher, times(1)).publishEvent(kafkaKey, kafkaValue, eiTopic, httpHeaders);
  }

  @Test
  public void testPublishContainerToInventoryDCVoidThrowException() {
    Container consolidatedContainer = mockContainer();
    InventoryDetails inventoryDetails = mockInventoryDetails();
    Map<String, Object> httpHeaders = new HashMap<>();
    populateTickTickHeaders(httpHeaders, inventoryDetails, ReceivingConstants.DC_VOIDS_EVENT_CODE);
    String kafkaKey = consolidatedContainer.getTrackingId();
    String kafkaValue = gson.toJson(inventoryDetails);
    String eiTopic = "dcVoid";
    doThrow(new RuntimeException())
        .when(rdcKafkaEIPublisher)
        .publishEvent(kafkaKey, kafkaValue, eiTopic, httpHeaders);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), EIService.ABORT_CALL_FOR_KAFKA_ERR, true))
        .thenReturn(true);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_TICK_TICK_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    try {
      eiService.publishContainerToEI(
          consolidatedContainer, inventoryDetails, ReceivingConstants.DC_VOID);
    } catch (Exception exception) {
    }
    verify(rdcKafkaEIPublisher, times(1)).publishEvent(kafkaKey, kafkaValue, eiTopic, httpHeaders);
    verify(configUtils, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), EIService.ABORT_CALL_FOR_KAFKA_ERR, true);
  }

  @Test
  public void testPublishContainerToInventoryDCVoidSkipException() {
    Container consolidatedContainer = mockContainer();
    InventoryDetails inventoryDetails = mockInventoryDetails();
    Map<String, Object> httpHeaders = new HashMap<>();
    String kafkaKey = consolidatedContainer.getTrackingId();
    String kafkaValue = gson.toJson(inventoryDetails);
    String eiTopic = "dcVoid";
    doThrow(new RuntimeException())
        .when(rdcKafkaEIPublisher)
        .publishEvent(kafkaKey, kafkaValue, eiTopic, httpHeaders);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), EIService.ABORT_CALL_FOR_KAFKA_ERR, true))
        .thenReturn(false);
    eiService.publishContainerToEI(
        consolidatedContainer, inventoryDetails, ReceivingConstants.DC_VOID);
    verify(rdcKafkaEIPublisher, times(1)).publishEvent(kafkaKey, kafkaValue, eiTopic, httpHeaders);
    verify(configUtils, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), EIService.ABORT_CALL_FOR_KAFKA_ERR, true);
  }

  /**
   * Preparation of Container
   *
   * @return
   */
  private Container mockContainer() {
    Container container = new Container();
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.BU_NUMBER, "02323");
    destination.put(ReceivingConstants.COUNTRY_CODE, "US");
    container.setDeliveryNumber(123L);
    container.setInstructionId(12345L);
    container.setTrackingId("r2323232308969587");
    container.setParentTrackingId(null);
    container.setCreateUser("sysadmin");
    container.setCompleteTs(new Date());
    container.setLastChangedUser("sysadmin");
    container.setLastChangedTs(new Date());
    container.setPublishTs(new Date());
    container.setDestination(destination);
    return container;
  }

  private InventoryDetails mockInventoryDetails() {
    InventoryDetails inventoryDetails = new InventoryDetails();
    try {
      inventoryDetails.setInventory(Collections.singletonList(new Inventory()));
      Inventory inventory = inventoryDetails.getInventory().get(0);
      inventory.setTrackingNumber("r2323232308969587");
      inventory.setIdempotentKey("r2323232308969587");
      Nodes nodes = new Nodes();
      Node toNode = new Node();
      toNode.setNodeId(1212);
      nodes.setToNode(toNode);
      inventory.setNodes(nodes);
      inventory.setEventInfo(prepareEventInfo());
      inventory.setChannelType(ReceivingConstants.DIST_CHANNEL_TYPE);
    } catch (Exception exception) {
    }
    return inventoryDetails;
  }

  /**
   * Preparation of EventInfo
   *
   * @return
   * @throws Exception
   */
  private EventInfo prepareEventInfo() {
    EventInfo eventInfo = new EventInfo();
    eventInfo.setProducerIdentifier(11);
    eventInfo.setCorelationId(
        Objects.isNull(TenantContext.getCorrelationId())
            ? UUID.randomUUID().toString()
            : TenantContext.getCorrelationId());
    return eventInfo;
  }

  private void populateTickTickHeaders(
      Map<String, Object> httpHeaders, InventoryDetails inventoryDetails, String eventType) {
    Inventory inventory = inventoryDetails.getInventory().get(0);
    httpHeaders.put(ReceivingConstants.HOP_ID, ReceivingConstants.ATLAS_RECEIVING_HOP_ID);
    httpHeaders.put(ReceivingConstants.TICK_TICK_TRACKING_ID, inventory.getIdempotentKey());
    httpHeaders.put(ReceivingConstants.EVENT_ID, inventory.getIdempotentKey());
    httpHeaders.put(ReceivingConstants.VERSION, ReceivingConstants.VERSION_1_0);
    HashMap<String, String> customFields = new HashMap<>();
    customFields.put(ReceivingConstants.EI_COUNTRY_CODE, ReceivingConstants.COUNTRY_CODE_US);
    if (Objects.nonNull(inventory.getNodes())) {
      if (ObjectUtils.allNotNull(
          inventory.getNodes().getFromNode(),
          inventory.getNodes().getToNode(),
          inventory.getNodes().getDestinationNode())) {
        customFields.put(
            ReceivingConstants.EI_STORE_NUMBER,
            inventory.getNodes().getDestinationNode().getNodeId().toString());
      } else {
        customFields.put(
            ReceivingConstants.EI_STORE_NUMBER,
            inventory.getNodes().getToNode().getNodeId().toString());
      }
    }
    customFields.put(ReceivingConstants.EI_EVENT_TYPE, eventType);
    httpHeaders.put(ReceivingConstants.CUSTOM_FIELDS, customFields);
  }

  @Test
  public void testPublishContainerToInventoryDCShipVoid() {
    Container consolidatedContainer = mockContainer();
    InventoryDetails inventoryDetails = mockInventoryDetails();
    Map<String, Object> httpHeaders = new HashMap<>();
    populateTickTickHeaders(httpHeaders, inventoryDetails, ReceivingConstants.DC_VOIDS_EVENT_CODE);
    String kafkaKey = consolidatedContainer.getTrackingId();
    String kafkaValue = gson.toJson(inventoryDetails);
    String eiTopic = "dcVoid";
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_TICK_TICK_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);

    doNothing().when(rdcKafkaEIPublisher).publishEvent(kafkaKey, kafkaValue, eiTopic, httpHeaders);
    eiService.publishContainerToEI(
        consolidatedContainer, inventoryDetails, ReceivingConstants.DC_SHIP_VOID);
    verify(rdcKafkaEIPublisher, times(1)).publishEvent(kafkaKey, kafkaValue, eiTopic, httpHeaders);
  }

  @Test
  public void testPublishContainerToInventoryDCTrueOut() {
    Container consolidatedContainer = mockContainer();
    InventoryDetails inventoryDetails = mockInventoryDetails();
    Map<String, Object> httpHeaders = new HashMap<>();
    populateTickTickHeaders(httpHeaders, inventoryDetails, ReceivingConstants.DC_VOIDS_EVENT_CODE);
    String kafkaKey = consolidatedContainer.getTrackingId();
    String kafkaValue = gson.toJson(inventoryDetails);
    String eiTopic = "dcVoid";
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_TICK_TICK_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);

    doNothing().when(rdcKafkaEIPublisher).publishEvent(kafkaKey, kafkaValue, eiTopic, httpHeaders);
    eiService.publishContainerToEI(
        consolidatedContainer, inventoryDetails, ReceivingConstants.DC_TRUE_OUT);
    verify(rdcKafkaEIPublisher, times(1)).publishEvent(kafkaKey, kafkaValue, eiTopic, httpHeaders);
  }
}
