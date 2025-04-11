package com.walmart.move.nim.receiving.mfc.message;

import com.walmart.move.nim.receiving.mfc.service.InventoryAdjustmentHelper;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;

public class HawkeyeAdjustmentListener {

  private static Logger LOGGER = LoggerFactory.getLogger(HawkeyeAdjustmentListener.class);

  @Autowired private InventoryAdjustmentHelper inventoryAdjustmentHelper;

  @Timed(
      name = "autoMFCDecantTimed",
      level1 = "uwms-receiving-api",
      level2 = "hawkeyeAdjustmentListener")
  @ExceptionCounted(
      name = "autoMFCDecantExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "hawkeyeAdjustmentListener")
  @KafkaListener(
      topics = "${hawkeye.mfc.inventory.state.change}",
      errorHandler = "kafkaErrorHandler",
      containerFactory = ReceivingConstants.HAWKEYE_SECURE_KAFKA_LISTENER_CONTAINER_FACTORY)
  public void listen(@Payload String message, @Header("eventType") byte[] event) {
    String eventType = new String(event);
    LOGGER.info("Entering to HawkeyeAdjustment Listener with eventType = {}", eventType);

    inventoryAdjustmentHelper.processInventoryAdjustment(message, eventType);

    LOGGER.info("Exiting from HawkeyeAdjustment Listener");
  }
}
