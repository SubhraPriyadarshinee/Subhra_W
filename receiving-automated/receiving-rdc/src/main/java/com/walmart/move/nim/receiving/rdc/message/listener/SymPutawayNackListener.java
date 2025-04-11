package com.walmart.move.nim.receiving.rdc.message.listener;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.symbotic.SymNackMessage;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Map;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.util.StringUtils;

public class SymPutawayNackListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(SymPutawayNackListener.class);

  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private Gson gson;
  /*
    This listener will consume the Putaway rejected messages sent by Symbotic to hawkeye.
    Atlas receiving will consume and trigger the splunk alert.
  * */
  @KafkaListener(
      topics = "${hawkeye.sym.putaway.nack.topic}",
      errorHandler = "kafkaErrorHandler",
      containerFactory = ReceivingConstants.HAWKEYE_SECURE_KAFKA_LISTENER_CONTAINER_FACTORY)
  @Timed(
      name = "consumeSymNackMessage",
      level1 = "uwms-receiving",
      level2 = "consumeSymNackMessage")
  @ExceptionCounted(
      name = "consumeSymNackMessage-Exception",
      level1 = "uwms-receiving",
      level2 = "consumeSymNackMessage-Exception")
  @TimeTracing(component = AppComponent.CORE, type = Type.MESSAGE, flow = "consumeSymNackMessage")
  public void listen(@Payload String message, @Headers Map<String, byte[]> kafkaHeaders) {

    String facilityNum = new String(kafkaHeaders.get(ReceivingConstants.TENENT_FACLITYNUM));
    if (!appConfig
        .getHawkeyeMessageListenerEnabledFacilities()
        .contains(Integer.valueOf(facilityNum))) {
      LOGGER.info(
          "Hawkeye message listener is not enabled for facility: {}, skipping this putaway nack message",
          facilityNum);
      return;
    }

    if (StringUtils.isEmpty(message)) {
      LOGGER.error(ReceivingConstants.WRONG_SYM_NACK_MESSAGE_FORMAT, message);
      return;
    }
    LOGGER.info("Received symbotic nack message = {}", message);

    try {
      SymNackMessage symNackMessage = gson.fromJson(message, SymNackMessage.class);
      String system = new String(kafkaHeaders.get(ReceivingConstants.SYM_SYSTEM_KEY));
      String messageId = new String(kafkaHeaders.get(ReceivingConstants.SYM_MESSAGE_ID_HEADER));
      String eventType = new String(kafkaHeaders.get(ReceivingConstants.SYM_EVENT_TYPE_KEY));
      String messageTS = new String(kafkaHeaders.get(ReceivingConstants.SYM_MSG_TIMESTAMP));
      String facilityCountryCode =
          new String(kafkaHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE));
      String labelId = new String(kafkaHeaders.get(ReceivingConstants.LABEL_ID_KEY));

      LOGGER.info(
          "Successfully consumed symbotic nack message from facilityNum = {}, facilityCountryCode = {}with system = {}, messageId = {}, eventType = {}, messageTs = {}, trackingId = {}, status = {}, reason = {}",
          facilityNum,
          facilityCountryCode,
          system,
          messageId,
          eventType,
          messageTS,
          labelId,
          symNackMessage.getStatus(),
          symNackMessage.getReason());
    } catch (Exception e) {
      LOGGER.error(
          "Unable to process symbotic nack message with error = {}",
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingInternalException(
          ExceptionCodes.SYMBOTIC_ERROR,
          String.format(
              ReceivingConstants.UNABLE_TO_PROCESS_SYM_NACK_ERROR_MSG,
              ExceptionUtils.getStackTrace(e)),
          e);
    }
  }
}
