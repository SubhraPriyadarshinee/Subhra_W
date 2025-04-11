package com.walmart.move.nim.receiving.sib.messsage.listener;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.sib.service.DeliveryStatusEventProcessor;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.util.StringUtils;

public class GdmDeliveryUpdateListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(GdmDeliveryUpdateListener.class);
  private static final String FLOW = "NGR-PP";

  @Autowired private DeliveryStatusEventProcessor deliveryStatusEventProcessor;

  // registering new consumer group
  @KafkaListener(
      topics = "${gdm.delivery.update.topic}",
      errorHandler = "kafkaErrorHandler",
      containerFactory = ReceivingConstants.ATLAS_SECURED_KAFKA_LISTENER_CONTAINER_FACTORY,
      groupId = "#{'${kafka.consumer.groupid:receiving-consumer}'.concat('-store-adapter')}")
  @Timed(
      name = "consumeDeliveryUpdate-ngr-parity",
      level1 = "uwms-receiving",
      level2 = "consumeDeliveryUpdate-ngr-parity")
  @ExceptionCounted(
      name = "consumeDeliveryUpdate-ngr-parity-Exception",
      level1 = "uwms-receiving",
      level2 = "consumeDeliveryUpdate-ngr-parity-Exception")
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.MESSAGE,
      flow = "consumeDeliveryUpdate-ngr-parity")
  public void listen(@Payload String message, @Headers Map<String, byte[]> kafkaHeaders) {
    LOGGER.info("Entering into GdmDeliveryUpdateListener");

    if (StringUtils.isEmpty(message)) {
      LOGGER.error(ReceivingConstants.WRONG_DELIVERY_UPDATE_MESSAGE_FORMAT, message);
      return;
    }

    String newCorrelationId =
        new StringBuilder(UUID.randomUUID().toString())
            .append(ReceivingConstants.DELIM_DASH)
            .append(FLOW)
            .toString();

    LOGGER.info(
        "Received delivery update message from GDM, message: {} and processing with new correlationId = {}",
        message,
        newCorrelationId);
    try {
      MDC.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, newCorrelationId);
      DeliveryUpdateMessage deliveryUpdateMessage =
          JacksonParser.convertJsonToObject(message, DeliveryUpdateMessage.class);

      deliveryStatusEventProcessor.doProcessEvent(deliveryUpdateMessage);

      LOGGER.info(
          "Successfully consumed GDM delivery update for delivery:{}",
          deliveryUpdateMessage.getDeliveryNumber());

    } catch (Exception excp) {
      LOGGER.error(
          "Unable to process GDM delivery update message - {}", ExceptionUtils.getStackTrace(excp));
      throw new ReceivingInternalException(
          ExceptionCodes.GDM_ERROR,
          String.format(ReceivingConstants.UNABLE_TO_PROCESS_DEL_UPDATE_ERROR_MSG, message),
          excp);
    } finally {
      MDC.clear();
    }
  }
}
