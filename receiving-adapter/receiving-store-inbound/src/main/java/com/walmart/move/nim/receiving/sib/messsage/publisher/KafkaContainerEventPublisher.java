package com.walmart.move.nim.receiving.sib.messsage.publisher;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CONTAINER_EVENT_PUBLISH_FLOW;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.KEY;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.sib.model.ContainerEventPublishingPayload;
import java.util.Date;
import java.util.Map;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;

public class KafkaContainerEventPublisher {

  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaContainerEventPublisher.class);
  private final Gson gson;

  public KafkaContainerEventPublisher() {
    gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  @Value("${container.event.data.topic}")
  private String containerEventDataTopic;

  @SecurePublisher private KafkaTemplate secureKafkaTemplate;

  public void publish(
      ContainerEventPublishingPayload containerEventPublishingPayload,
      Map<String, Object> messageHeader) {

    // converting container Object into String
    String payload = gson.toJson(containerEventPublishingPayload);
    String kafkaKey = (String) messageHeader.get(KEY);
    LOGGER.info("Kafka key for transaction is {}", kafkaKey);

    try {
      Message<String> message =
          KafkaHelper.buildKafkaMessage(kafkaKey, payload, containerEventDataTopic, messageHeader);
      secureKafkaTemplate.send(message).get();
      LOGGER.info(
          "Secure Kafka: Successfully sent the event list = {} to CSM = {}",
          payload,
          containerEventDataTopic);
    } catch (Exception exception) {
      LOGGER.error("Unable to send message to Kafka {}", ExceptionUtils.getStackTrace(exception));
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE,
          String.format(KAFKA_NOT_ACCESSIBLE_ERROR_MSG, CONTAINER_EVENT_PUBLISH_FLOW));
    }
  }
}
