package com.walmart.move.nim.receiving.endgame.message.listener;

import com.google.gson.Gson;
import com.walmart.atlas.global.config.annotation.EnableInPrimaryRegionNode;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.message.common.UpdateAttributesData;
import com.walmart.move.nim.receiving.endgame.service.EndgameContainerService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Map;
import javax.annotation.Resource;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;

public class SecureUpdateAttributeListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(SecureUpdateAttributeListener.class);
  @Autowired private EndgameContainerService endgameContainerService;

  @KafkaListener(
      topics = "#{'${decant.update.topic}'.split(',')}",
      errorHandler = "kafkaErrorHandler",
      containerFactory = ReceivingConstants.ATLAS_SECURED_KAFKA_LISTENER_CONTAINER_FACTORY)
  public void listen(@Payload String message, @Headers Map<String, byte[]> kafkaHeaders) {
    LOGGER.info("Secure Kafka : Got Update Attribute Listener : start");
    if (ObjectUtils.isEmpty(message)) {
      LOGGER.error(ReceivingConstants.LOG_WRONG_MESSAGE_FORMAT, message);
      return;
    }
    endgameContainerService.processContainerUpdates(message);
    LOGGER.info("Secure Kafka : Got Update Attribute Listener : end");
  }
}
