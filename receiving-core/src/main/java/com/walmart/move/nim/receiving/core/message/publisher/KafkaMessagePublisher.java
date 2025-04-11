package com.walmart.move.nim.receiving.core.message.publisher;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.SPLUNK_ALERT;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.service.RapidRelayerService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component(ReceivingConstants.KAFKA_PUBLISHER)
public class KafkaMessagePublisher {

  private static final Logger LOG = LoggerFactory.getLogger(KafkaMessagePublisher.class);
  private Gson gson;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  public KafkaMessagePublisher() {
    gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  @SecurePublisher private KafkaTemplate secureKafkaTemplate;
  @Autowired private RapidRelayerService rapidRelayerService;

  /**
   * * This function publish kafka message using SecurePublisher
   *
   * @param kafkaKey the key of kafka message
   * @param kafkaPayload the payload of kafka message
   * @param topic the topicName of kafka message
   * @param headers the headers of kafka message
   */
  public void publish(
      String kafkaKey, Object kafkaPayload, String topic, Map<String, Object> headers)
      throws ReceivingInternalException {
    Message<String> message = null;
    try {
      String payload = gson.toJson(kafkaPayload);
      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          String.valueOf(headers.get(ReceivingConstants.TENENT_FACLITYNUM)),
          ReceivingConstants.OUTBOX_PATTERN_ENABLED,
          false)) {
        rapidRelayerService.produceMessage(topic, kafkaKey, payload, headers);
      } else {
        message =
            KafkaHelper.buildKafkaMessage(
                kafkaKey,
                payload,
                topic,
                ReceivingUtils.enrichKafkaHeaderForRapidRelayer(
                    headers,
                    Objects.isNull(TenantContext.getCorrelationId())
                        ? UUID.randomUUID().toString()
                        : TenantContext.getCorrelationId()));
        LOG.info("Publishing kafkaKey={} to kafka topic={} message={}", kafkaKey, topic, message);
        secureKafkaTemplate.send(message);
        LOG.info("Secure Kafka:Successfully published to topic = {}", topic);
      }
    } catch (Exception exception) {
      LOG.error(
          "{}Secure Kafka:Error in publishing topic {} kafkaKey {} message {} with exception - {}",
          SPLUNK_ALERT,
          topic,
          kafkaKey,
          message,
          ExceptionUtils.getStackTrace(exception));
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE, ReceivingConstants.KAFKA_UNABLE_TO_SEND_ERROR_MSG);
    }
  }
}
