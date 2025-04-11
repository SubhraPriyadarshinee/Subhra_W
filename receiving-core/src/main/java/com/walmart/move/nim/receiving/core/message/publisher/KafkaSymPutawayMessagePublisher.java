package com.walmart.move.nim.receiving.core.message.publisher;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
import com.walmart.move.nim.receiving.core.model.symbotic.SymPutawayMessage;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Date;
import java.util.Map;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component(ReceivingConstants.KAFKA_HAWKEYE_PUTAWAY_PUBLISHER)
public class KafkaSymPutawayMessagePublisher implements MessagePublisher<SymPutawayMessage> {

  private static final Logger LOG = LoggerFactory.getLogger(KafkaSymPutawayMessagePublisher.class);

  @Value("${hawkeye.sym.putaway.topic}")
  private String hawkeyeSymPutawayTopic;

  @ManagedConfiguration private KafkaConfig kafkaConfig;

  @SecurePublisher KafkaTemplate secureKafkaTemplate;

  private Gson gson;

  public KafkaSymPutawayMessagePublisher() {
    gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  @Override
  public void publish(SymPutawayMessage symPutawayMessage, Map<String, Object> messageHeader) {
    String payload = gson.toJson(symPutawayMessage);
    String eventType = (String) messageHeader.get(ReceivingConstants.SYM_EVENT_TYPE_KEY);
    String action = symPutawayMessage.getAction();

    try {
      Message<String> message =
          KafkaHelper.buildKafkaMessage(
              symPutawayMessage.getTrackingId(), payload, hawkeyeSymPutawayTopic, messageHeader);
      secureKafkaTemplate.send(message);
      LOG.info(
          "Successfully published the putaway message to Secure Kafka = {} , action = {}, eventType = {}, to topic = {}",
          payload,
          action,
          eventType,
          hawkeyeSymPutawayTopic);
    } catch (Exception exception) {
      LOG.error(
          "Error in publishing putaway  message to hawkeye for tracking id {} with exception - {}",
          symPutawayMessage.getTrackingId(),
          ExceptionUtils.getStackTrace(exception));
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE,
          String.format(
              KAFKA_NOT_ACCESSIBLE_ERROR_MSG, ReceivingConstants.HAWKEYE_SYM_PUTAWAY_PUBLISH_FLOW));
    }
  }
}
