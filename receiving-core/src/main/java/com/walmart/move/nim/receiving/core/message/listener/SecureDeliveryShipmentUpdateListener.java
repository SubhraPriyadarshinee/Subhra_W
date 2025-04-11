package com.walmart.move.nim.receiving.core.message.listener;

import com.walmart.move.nim.receiving.core.helper.DeliveryShipmentUpdateHelper;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;

public class SecureDeliveryShipmentUpdateListener {

  private static Logger LOGGER =
      LoggerFactory.getLogger(SecureDeliveryShipmentUpdateListener.class);
  @Autowired private DeliveryShipmentUpdateHelper deliveryShipmentUpdateHelper;

  @KafkaListener(
      topics = "${gdm.update.topic}",
      errorHandler = "kafkaErrorHandler",
      containerFactory = ReceivingConstants.ATLAS_SECURED_KAFKA_LISTENER_CONTAINER_FACTORY)
  public void listen(@Payload String message, @Headers Map<String, byte[]> kafkaHeaders) {
    LOGGER.info("Entering to secure SecureDeliveryShipmentUpdateListener");
    deliveryShipmentUpdateHelper.doProcess(message);
    LOGGER.info("Exiting from secure SecureDeliveryShipmentUpdateListener");
  }
}
