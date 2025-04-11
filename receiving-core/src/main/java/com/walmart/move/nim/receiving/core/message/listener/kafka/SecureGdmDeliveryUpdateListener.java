package com.walmart.move.nim.receiving.core.message.listener.kafka;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.helper.GdmDeliveryUpdateListenerHelper;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;

public class SecureGdmDeliveryUpdateListener {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(SecureGdmDeliveryUpdateListener.class);

  @Autowired private GdmDeliveryUpdateListenerHelper gdmDeliveryUpdateListenerHelper;

  @KafkaListener(
      topics = "${gdm.delivery.update.topic}",
      errorHandler = "kafkaErrorHandler",
      containerFactory = ReceivingConstants.ATLAS_SECURED_KAFKA_LISTENER_CONTAINER_FACTORY,
      groupId = "${gdm.delivery.update.kafka.consumer.groupid:receiving-consumer}",
      concurrency = "${gdm.delivery.update.kafka.consumer.threads:1}")
  @Timed(
      name = "consumeDeliveryUpdate",
      level1 = "uwms-receiving",
      level2 = "consumeDeliveryUpdate")
  @ExceptionCounted(
      name = "consumeDeliveryUpdate-Exception",
      level1 = "uwms-receiving",
      level2 = "consumeDeliveryUpdate-Exception")
  @TimeTracing(component = AppComponent.CORE, type = Type.MESSAGE, flow = "consumeDeliveryUpdate")
  public void listen(@Payload String message, @Headers Map<String, byte[]> kafkaHeaders) {
    LOGGER.info("Entering into SecureGdmDeliveryUpdateListener");
    gdmDeliveryUpdateListenerHelper.doProcess(message, kafkaHeaders);
    LOGGER.info("Exiting from SecureGdmDeliveryUpdateListener");
  }
}
