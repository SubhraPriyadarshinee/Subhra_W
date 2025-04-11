package com.walmart.move.nim.receiving.acc.message.listener;

import com.walmart.move.nim.receiving.acc.model.RoboDepalEventMessage;
import com.walmart.move.nim.receiving.acc.service.RoboDepalEventProcessor;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.platform.scm.client.shared.logging.Logger;
import com.walmart.platform.scm.client.shared.logging.LoggerFactory;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;

public class RoboDepalContainerStatusListener {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(RoboDepalContainerStatusListener.class);
  @Autowired TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired RoboDepalEventProcessor depalEventProcessor;

  @KafkaListener(
      topics = "${robo.depal.kafka.topic}",
      errorHandler = "kafkaErrorHandler",
      containerFactory = ReceivingConstants.HAWKEYE_SECURE_KAFKA_LISTENER_CONTAINER_FACTORY,
      groupId = "#{'${kafka.consumer.groupid:receiving-consumer}'.concat('-hawkeye-ctnr-status')}")
  @Timed(
      name = "roboDepalContainerStatusChange",
      level1 = "uwms-receiving",
      level2 = "roboDepalContainerStatusChange")
  @ExceptionCounted(
      name = "roboDepalContainerStatusChange-Exception",
      level1 = "uwms-receiving",
      level2 = "roboDepalContainerStatusChange-Exception")
  @TimeTracing(component = AppComponent.CORE, type = Type.MESSAGE, flow = "roboDepal")
  public void listen(@Payload String message, @Headers Map<String, byte[]> kafkaHeaders) {
    LOGGER.info("Received Robo Depal container status change message: {}", message);
    processListener(message);
  }

  private void processListener(String message) {
    if (StringUtils.isEmpty(message)) {
      LOGGER.error(ReceivingConstants.WRONG_ROBO_DEPAL_LISTENER_MESSAGE_FORMAT, message);
      return;
    }
    try {
      RoboDepalEventMessage depalEventMessage =
          JacksonParser.convertJsonToObject(message, RoboDepalEventMessage.class);
      if (!tenantSpecificConfigReader.isFeatureFlagEnabled(
          ReceivingConstants.ROBO_DEPAL_FEATURE_ENABLED)) {
        LOGGER.warn(
            "Robo Depal feature is not enabled for facility: {}, ignoring the message: {}",
            TenantContext.getFacilityNum(),
            message);
        return;
      }
      depalEventProcessor.processEvent(depalEventMessage);
      LOGGER.info(
          "Successfully consumed Robo Depal container status change message for docktag: {}",
          depalEventMessage.getTrackingId());
    } catch (Exception e) {
      LOGGER.error(
          String.format(
              ReceivingConstants.UNABLE_TO_PROCESS_ROBO_DEPAL_EVENT_ERROR_MSG,
              message,
              ExceptionUtils.getStackTrace(e)));
    } finally {
      TenantContext.clear();
    }
  }
}
