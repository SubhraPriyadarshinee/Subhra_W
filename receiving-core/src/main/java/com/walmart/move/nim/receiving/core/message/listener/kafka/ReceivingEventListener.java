package com.walmart.move.nim.receiving.core.message.listener.kafka;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.atlas.global.config.annotation.EnableInPrimaryRegionNodeCondition;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.framework.message.processor.ProcessExecutor;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Date;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Conditional;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Self loop listener for distributed message processing. Note : Please make sure ,
 * underlaying @{@link ProcessExecutor} implementation taking care of duplicate message processing
 * as well as kafka offset commit. Otherwise, it may rebalance the listener
 *
 * @author sitakant
 */
@ConditionalOnExpression("${enable.self.loop.processing:false}")
@Conditional(EnableInPrimaryRegionNodeCondition.class)
@Component
public class ReceivingEventListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReceivingEventListener.class);

  private Gson gson;

  @Autowired private ProcessInitiator processInitiator;

  public ReceivingEventListener() {
    this.gson =
        new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  @KafkaListener(
      topics = "${receiving.self.process.topic}",
      errorHandler = "kafkaErrorHandler",
      containerFactory = ReceivingConstants.ATLAS_SECURED_KAFKA_LISTENER_CONTAINER_FACTORY)
  @Timed(
      name = "receivingSelfloopProcessing",
      level1 = "uwms-receiving",
      level2 = "receivingSelfloopProcessing")
  @ExceptionCounted(
      name = "receivingSelfloopProcessing-Exception",
      level1 = "uwms-receiving",
      level2 = "receivingSelfloopProcessing-Exception")
  public void listen(@Payload String message, @Headers Map<String, byte[]> kafkaHeaders) {
    LOGGER.info("Got message into self loop . message = {}", message);
    ReceivingEvent receivingEvent = gson.fromJson(message, ReceivingEvent.class);
    ProcessExecutor processExecutor = processInitiator.loadProcessExecutor(receivingEvent);
    LOGGER.info("Selected ProcessExecutor = {} ", processExecutor);
    processExecutor.doExecute(receivingEvent);
  }
}
