package com.walmart.move.nim.receiving.core.handler;

import io.strati.metrics.annotation.ExceptionCounted;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.KafkaListenerErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public class KafkaErrorHandler implements KafkaListenerErrorHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaErrorHandler.class);

  @ExceptionCounted(
      name = "kafkaListenerExceptionCount",
      level1 = "uwms-receiving",
      level2 = "kafkaExceptionHandler")
  @Override
  public Object handleError(Message<?> message, ListenerExecutionFailedException e) {
    LOGGER.info("Error in kafka listener . Message = {}", message.getPayload());
    LOGGER.error("Error occured {}", ExceptionUtils.getStackTrace(e));
    triggerAlert(e);
    return null;
  }

  @ExceptionCounted(
      name = "kafkaListenerExceptionCount",
      level1 = "uwms-receiving",
      level2 = "kafkaExceptionHandler")
  @Override
  public Object handleError(
      Message<?> message, ListenerExecutionFailedException exception, Consumer<?, ?> consumer) {
    LOGGER.info("Error in kafka listener . Message = {}", message.getPayload());
    LOGGER.error("Error occured {}", ExceptionUtils.getStackTrace(exception));
    triggerAlert(exception);
    return null;
  }

  private void triggerAlert(Exception e) {
    // TODO Logic goes here
  }
}
