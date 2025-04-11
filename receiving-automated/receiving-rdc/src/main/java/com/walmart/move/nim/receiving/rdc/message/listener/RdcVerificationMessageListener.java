package com.walmart.move.nim.receiving.rdc.message.listener;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.model.symbotic.RdcVerificationMessage;
import com.walmart.move.nim.receiving.rdc.service.RdcVerificationEventProcessor;
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
import org.springframework.util.ObjectUtils;

public class RdcVerificationMessageListener {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(RdcVerificationMessageListener.class);
  @Autowired private Gson gson;
  @Autowired private RdcVerificationEventProcessor rdcVerificationEventProcessor;

  @KafkaListener(
      topics = "${rdc.kafka.verification.topic}",
      errorHandler = "kafkaErrorHandler",
      containerFactory = ReceivingConstants.HAWKEYE_SECURE_KAFKA_LISTENER_CONTAINER_FACTORY)
  @Timed(
      name = "rdcConsumeVerificationMessage",
      level1 = "uwms-receiving",
      level2 = "rdcConsumeVerificationMessage")
  @ExceptionCounted(
      name = "rdcConsumeVerificationMessage-Exception",
      level1 = "uwms-receiving",
      level2 = "rdcConsumeVerificationMessage-Exception")
  @TimeTracing(
      component = AppComponent.RDC,
      type = Type.MESSAGE,
      flow = "rdcConsumeVerificationMessage")
  public void listen(@Payload String message, @Headers Map<String, byte[]> kafkaHeaders) {
    LOGGER.info("Received verification message from hawkeye, message: {}", message);

    if (ObjectUtils.isEmpty(message)) {
      LOGGER.error(ReceivingConstants.WRONG_VERIFICATION_MESSAGE_FORMAT, message);
      return;
    }
    try {
      RdcVerificationMessage rdcVerificationMessage =
          gson.fromJson(message, RdcVerificationMessage.class);
      rdcVerificationMessage.setHttpHeaders(
          ReceivingUtils.populateHttpHeadersFromKafkaHeaders(kafkaHeaders));
      rdcVerificationEventProcessor.processEvent(rdcVerificationMessage);
    } catch (Exception excp) {
      throw new ReceivingInternalException(
          ExceptionCodes.SYMBOTIC_ERROR,
          String.format(ReceivingConstants.UNABLE_TO_PROCESS_VERIFICATION_EVENT_ERROR_MSG, message),
          excp);
    }
  }
}
