package com.walmart.move.nim.receiving.core.message.listener.kafka;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.common.ItemUpdateEventType;
import com.walmart.move.nim.receiving.core.message.common.ItemUpdateMessage;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;

public class SecureGdmItemUpdateListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(SecureGdmItemUpdateListener.class);
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @KafkaListener(
      topics = "${gdm.item.update.topic:ATLAS_GDM_ITEM_UPDATE_EVENTS_DEV}",
      errorHandler = "kafkaErrorHandler",
      containerFactory = ReceivingConstants.ATLAS_SECURED_KAFKA_LISTENER_CONTAINER_FACTORY,
      groupId = "#{'${kafka.consumer.groupid:receiving-consumer}'.concat('-item-update')}",
      concurrency = "${gdm.item.update.kafka.consumer.threads:1}")
  @Timed(name = "itemUpdate", level1 = "uwms-receiving", level2 = "itemUpdate")
  @ExceptionCounted(
      name = "consumeItemUpdate-Exception",
      level1 = "uwms-receiving",
      level2 = "consumeItemUpdate-Exception")
  @TimeTracing(component = AppComponent.CORE, type = Type.MESSAGE, flow = "itemUpdate")
  public void listen(@Payload String message, @Headers Map<String, byte[]> kafkaHeaders) {
    processListener(message, kafkaHeaders);
  }

  private void processListener(String message, Map<String, byte[]> kafkaHeaders) {
    LOGGER.info("Received item update message from GDM, message: {}", message);
    if (StringUtils.isEmpty(message)) {
      LOGGER.error(ReceivingConstants.WRONG_ITEM_UPDATE_MESSAGE_FORMAT, message);
      return;
    }
    try {
      ItemUpdateMessage itemUpdateMessage =
          JacksonParser.convertJsonToObject(message, ItemUpdateMessage.class);
      if (Objects.isNull(itemUpdateMessage) || Objects.isNull(itemUpdateMessage.getItemNumber())) {
        LOGGER.error(ReceivingConstants.WRONG_ITEM_UPDATE_MESSAGE_FORMAT, message);
        return;
      }
      Integer facilityNum = TenantContext.getFacilityNum();
      if (!CollectionUtils.emptyIfNull(appConfig.getGdmItemUpdateListenerEnabledFacilities())
          .contains(facilityNum)) {
        LOGGER.warn(
            "Consuming GDM item update events from kafka listener is not enabled for facility: {}, skipping this message: {}",
            facilityNum,
            itemUpdateMessage);
        return;
      }
      if (!validateItemUpdateEvent(itemUpdateMessage)) {
        LOGGER.warn(
            "Consuming item update event: {} is not enabled for facility: {}, skipping this message: {}",
            itemUpdateMessage.getEventType(),
            facilityNum,
            itemUpdateMessage);
        return;
      }
      itemUpdateMessage.setHttpHeaders(
          ReceivingUtils.populateHttpHeadersFromKafkaHeaders(kafkaHeaders));
      EventProcessor itemUpdateProcessor =
          tenantSpecificConfigReader.getConfiguredInstance(
              facilityNum.toString(),
              ReceivingConstants.ITEM_UPDATE_PROCESSOR,
              EventProcessor.class);
      itemUpdateProcessor.processEvent(itemUpdateMessage);
      LOGGER.info(
          "Successfully consumed item update event: {} for item: {}",
          itemUpdateMessage.getEventType(),
          itemUpdateMessage.getItemNumber());
    } catch (ReceivingException excp) {
      LOGGER.error(
          "Unable to process GDM item update message: {}", ExceptionUtils.getStackTrace(excp));
      throw new ReceivingInternalException(
          ExceptionCodes.UNABLE_TO_PROCESS_ITEM_UPDATE,
          String.format(ReceivingConstants.UNABLE_TO_PROCESS_ITEM_UPDATE_ERROR_MSG, message),
          excp);
    } finally {
      TenantContext.clear();
    }
  }

  boolean validateItemUpdateEvent(ItemUpdateMessage itemUpdateMessage) {
    ItemUpdateEventType eventType;
    try {
      eventType = ItemUpdateEventType.valueOf(itemUpdateMessage.getEventType());
    } catch (IllegalArgumentException e) {
      LOGGER.warn(
          "Invalid item update event: {} received for item: {}",
          itemUpdateMessage.getEventType(),
          itemUpdateMessage.getItemNumber());
      return false;
    }
    switch (eventType) {
      case HANDLING_CODE_UPDATE:
        return tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.ITEM_HANDLING_CODE_UPDATE_ENABLED);
      case CHANNEL_FLIP:
        return tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.ITEM_CHANNEL_FLIP_ENABLED);
      case CATALOG_GTIN_UPDATE:
        return checkIfCatalogEventIsValid(itemUpdateMessage);
      default:
        return false;
    }
  }

  boolean checkIfCatalogEventIsValid(ItemUpdateMessage itemUpdateMessage) {
    String to = itemUpdateMessage.getTo();
    if (StringUtils.isBlank(to) || ReceivingConstants.EMPTY_CATALOG_GTIN.equals(to)) {
      return tenantSpecificConfigReader.isFeatureFlagEnabled(
          ReceivingConstants.ITEM_UNDO_CATALOG_ENABLED);
    } else {
      return tenantSpecificConfigReader.isFeatureFlagEnabled(
          ReceivingConstants.ITEM_CATALOG_UPDATE_ENABLED);
    }
  }
}
