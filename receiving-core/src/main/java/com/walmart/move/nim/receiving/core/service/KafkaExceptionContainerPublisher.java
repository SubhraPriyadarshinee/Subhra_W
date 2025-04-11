package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
import com.walmart.move.nim.receiving.core.message.publisher.MessagePublisher;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
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

@Component(ReceivingConstants.KAFKA_EXCEPTION_CONTAINER_PUBLISHER)
public class KafkaExceptionContainerPublisher implements MessagePublisher<ContainerDTO> {

  private final Gson gson;
  @SecurePublisher private KafkaTemplate secureKafkaTemplate;

  @Value("${topic.receiving.exception.container:TOPIC_RECEIVE_EXCEPTION_CONTAINERS_DEV}")
  private String exceptionContainerTopic;

  @ManagedConfiguration KafkaConfig kafkaConfig;

  private static final Logger log = LoggerFactory.getLogger(ContainerService.class);

  public KafkaExceptionContainerPublisher() {
    gson =
        new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter())
            .create();
  }

  @Override
  public void publish(ContainerDTO container, Map<String, Object> messageHeader) {
    String payload = gson.toJson(container);
    Object kafkaKey =
        !org.apache.commons.lang.StringUtils.isEmpty(payload)
            ? container.getTrackingId()
            : ReceivingConstants.DEFAULT_KAFKA_KEY;
    log.info("Kafka key for publishing container message is: {}", kafkaKey);

    try {

      Message<String> message =
          KafkaHelper.buildKafkaMessage(kafkaKey, payload, exceptionContainerTopic);
      if (kafkaConfig.isInventoryOnSecureKafka()) {
        secureKafkaTemplate.send(message);
        log.info(
            "Secure Kafka: Successfully published docktag container: {} on topic: {}",
            container.getTrackingId(),
            exceptionContainerTopic);
      }

    } catch (Exception exception) {
      log.error(
          "Error in publishing exception container: {} and error: {}",
          container.getTrackingId(),
          ExceptionUtils.getStackTrace(exception));
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE,
          String.format(KAFKA_NOT_ACCESSIBLE_ERROR_MSG, ReceivingConstants.CONTAINERS_PUBLISH));
    }
  }
}
