package com.walmart.move.nim.receiving.core.message.listener.kafka;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.InventoryAdjustmentTO;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Map;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.util.StringUtils;

public class SecureKafkaInventoryAdjustmentListener {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(SecureKafkaInventoryAdjustmentListener.class);

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private Gson gson;
  @ManagedConfiguration private AppConfig appConfig;

  @KafkaListener(
      topics = "${inventory.adjustment.message.topic}",
      errorHandler = "kafkaErrorHandler",
      containerFactory = ReceivingConstants.ATLAS_SECURED_KAFKA_LISTENER_CONTAINER_FACTORY,
      groupId = "#{'${kafka.consumer.groupid:receiving-consumer}'.concat('-inventory')}",
      concurrency = "${inventory.adjustment.kafka.consumer.threads:1}")
  @Timed(
      name = "consumeSecureKafkaInventoryAdjustment",
      level1 = "uwms-receiving",
      level2 = "consumeSecureKafkaInventoryAdjustment")
  @ExceptionCounted(
      name = "consumeSecureKafkaInventoryAdjustment-Exception",
      level1 = "uwms-receiving",
      level2 = "consumeSecureKafkaInventoryAdjustment-Exception")
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.MESSAGE,
      flow = "consumeSecureKafkaInventoryAdjustment")
  public void listen(@Payload String message, @Headers Map<String, byte[]> kafkaHeaders) {
    try {
      LOGGER.info(
          "SecureKafkaInventoryAdjustmentListener - consumed inventory adjustment message ....");
      Integer facilityNum = TenantContext.getFacilityNum();

      if (!appConfig.getKafkaInventoryAdjustmentListenerEnabledFacilities().contains(facilityNum)) {
        LOGGER.info(
            "Secure Kafka inventory adjustment listener is not enabled for facility: {}, skipping this adjustment message",
            facilityNum);
        return;
      }
      if (StringUtils.isEmpty(message)) {
        LOGGER.error(
            String.format(ReceivingConstants.INVALID_ADJUSTMENT_MESSAGE_FROM_INVENTORY, message));
        return;
      }
      HttpHeaders httpHeaders =
          ReceivingUtils.populateInventoryHeadersFromKafkaHeaders(kafkaHeaders);

      InventoryAdjustmentTO inventoryAdjustmentTO = new InventoryAdjustmentTO();
      JsonObject inventoryAdjustmentJsonObject = (JsonObject) new JsonParser().parse(message);
      inventoryAdjustmentTO.setJsonObject(inventoryAdjustmentJsonObject);
      inventoryAdjustmentTO.setHttpHeaders(httpHeaders);

      LOGGER.info(
          "Received inventory adjustment message in Secure kafka:{} and headers: {}",
          inventoryAdjustmentJsonObject,
          httpHeaders);

      EventProcessor inventoryAdjustmentProcessor =
          tenantSpecificConfigReader.getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.KAFKA_INVENTORY_ADJUSTMENT_PROCESSOR,
              EventProcessor.class);
      inventoryAdjustmentProcessor.processEvent(inventoryAdjustmentTO);
      TenantContext.clear();
    } catch (Exception err) {
      LOGGER.error(
          "Exception occurred processing inventory adjustment : {}",
          ExceptionUtils.getStackTrace(err));
    }
  }
}
