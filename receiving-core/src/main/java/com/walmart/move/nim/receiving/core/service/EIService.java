package com.walmart.move.nim.receiving.core.service;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.message.publisher.RdcKafkaEIPublisher;
import com.walmart.move.nim.receiving.core.model.ei.Inventory;
import com.walmart.move.nim.receiving.core.model.ei.InventoryDetails;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

@ConditionalOnExpression(ReceivingConstants.ENABLE_EI_KAFKA)
@Service
public class EIService {

  @Autowired private TenantSpecificConfigReader configUtils;
  @Autowired private RdcKafkaEIPublisher rdcKafkaEIPublisher;
  @Autowired private Gson gson;

  @Value("${ei.dc.receiving.event.topic}")
  private String dcReceivingTopic;

  @Value("${ei.dc.picks.event.topic}")
  private String dcPicksTopic;

  @Value("${ei.dc.void.event.topic}")
  private String dcVoidTopic;

  public static final String ABORT_CALL_FOR_KAFKA_ERR = "abortCallForKafkaErr";

  public EIService() {}

  /**
   * Publish container to EI
   *
   * @param consolidatedContainer
   * @param inventoryDetails
   * @param transformType
   */
  public void publishContainerToEI(
      Container consolidatedContainer, InventoryDetails inventoryDetails, String transformType) {
    String kafkaKey = null;
    String kafkaValue = null;
    String eiTopic = null;
    try {
      Map<String, Object> httpHeaders = new HashMap<>();
      kafkaKey = consolidatedContainer.getTrackingId();
      kafkaValue = gson.toJson(inventoryDetails);
      eiTopic = mapEITopic(transformType);
      populateTickTickHeaders(httpHeaders, inventoryDetails, transformType);
      rdcKafkaEIPublisher.publishEvent(kafkaKey, kafkaValue, eiTopic, httpHeaders);
    } catch (Exception exception) {
      if (configUtils.getConfiguredFeatureFlag(
          TenantContext.getFacilityNum().toString(), ABORT_CALL_FOR_KAFKA_ERR, true)) {
        throw new ReceivingInternalException(
            ExceptionCodes.KAFKA_NOT_ACCESSABLE,
            String.format(
                ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG,
                ReceivingConstants.MULTIPLE_PALLET_RECEIVING_FLOW));
      }
    }
  }

  /**
   * @param httpHeaders
   * @param inventoryDetails
   * @param transformType
   */
  public void populateTickTickHeaders(
      Map<String, Object> httpHeaders, InventoryDetails inventoryDetails, String transformType) {
    if (configUtils.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.IS_TICK_TICK_INTEGRATION_ENABLED,
        false)) {
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
      customFields.put(ReceivingConstants.EI_EVENT_TYPE, mapTickTickEventType(transformType));
      httpHeaders.put(ReceivingConstants.CUSTOM_FIELDS, customFields);
    }
  }

  /**
   * Map ei topic
   *
   * @param transformType
   * @return
   */
  private String mapEITopic(String transformType) {
    String eiTopic = null;
    switch (transformType) {
      case ReceivingConstants.DC_RECEIVING:
        eiTopic = dcReceivingTopic;
        break;
      case ReceivingConstants.DC_PICKS:
        eiTopic = dcPicksTopic;
        break;
      case ReceivingConstants.DC_VOID:
      case ReceivingConstants.DC_SHIP_VOID:
      case ReceivingConstants.DC_TRUE_OUT:
      case ReceivingConstants.DC_XDK_VOID:
        eiTopic = dcVoidTopic;
        break;
    }
    return eiTopic;
  }

  /**
   * Map ei Event Type
   *
   * @param transformType
   * @return
   */
  private String mapTickTickEventType(String transformType) {
    String eventTypeCode = null;
    switch (transformType) {
      case ReceivingConstants.DC_RECEIVING:
        eventTypeCode = ReceivingConstants.DC_RECEIVING_EVENT_CODE;
        break;
      case ReceivingConstants.DC_PICKS:
        eventTypeCode = ReceivingConstants.DC_PICK_EVENT_CODE;
        break;
      case ReceivingConstants.DC_VOID:
      case ReceivingConstants.DC_SHIP_VOID:
      case ReceivingConstants.DC_TRUE_OUT:
      case ReceivingConstants.DC_XDK_VOID:
        eventTypeCode = ReceivingConstants.DC_VOIDS_EVENT_CODE;
        break;
    }
    return eventTypeCode;
  }
}
