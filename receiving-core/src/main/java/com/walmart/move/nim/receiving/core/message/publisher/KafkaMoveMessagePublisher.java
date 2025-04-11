package com.walmart.move.nim.receiving.core.message.publisher;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.apache.commons.lang.StringUtils.isBlank;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
import com.walmart.move.nim.receiving.core.model.move.CancelMove;
import com.walmart.move.nim.receiving.core.model.move.MoveData;
import com.walmart.move.nim.receiving.core.model.move.MoveInfo;
import com.walmart.move.nim.receiving.core.service.RapidRelayerService;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component(ReceivingConstants.KAFKA_MOVE_PUBLISHER)
public class KafkaMoveMessagePublisher implements MessagePublisher<MessageData> {

  private static final Logger LOG = LoggerFactory.getLogger(KafkaMoveMessagePublisher.class);

  private Gson gson;

  public KafkaMoveMessagePublisher() {
    gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  @ManagedConfiguration KafkaConfig kafkaConfig;

  @SecurePublisher private KafkaTemplate secureKafkaTemplate;
  @Autowired private RapidRelayerService rapidRelayerService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Value("${atlas.move.topic:null}")
  private String moveTopic;

  @Override
  public void publish(MessageData movePayload, Map<String, Object> messageHeaders) {
    String key =
        movePayload instanceof MoveData
            ? ((MoveData) movePayload).getContainerTag()
            : movePayload instanceof MoveInfo ? ((MoveInfo) movePayload).getContainerTag() : null;
    if (isBlank(key)) {
      LOG.info("Unable to publish move message due to invalid payload={} ", movePayload);
      return;
    }
    // publish message
    publishMessageToKafka(key, movePayload, messageHeaders);
  }

  public void publishCancelMove(CancelMove cancelMove, Map<String, Object> messageHeaders) {
    LOG.info("Publishing cancel move message for container {}", cancelMove.getContainerTag());
    // publish message
    publishMessageToKafka(cancelMove.getContainerTag(), cancelMove, messageHeaders);
  }

  private void publishMessageToKafka(
      String containerTag, Object deliveryMessage, Map<String, Object> headers) {
    try {
      LOG.info(
          "Publishing move message for container {} to kafka topic {}", containerTag, moveTopic);
      String payload = gson.toJson(deliveryMessage);
      String correlationId =
          Objects.isNull(TenantContext.getCorrelationId())
              ? UUID.randomUUID().toString()
              : TenantContext.getCorrelationId();
      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          TenantContext.getFacilityNum().toString(),
          ReceivingConstants.OUTBOX_PATTERN_ENABLED,
          false)) {
        rapidRelayerService.produceMessage(
            moveTopic,
            containerTag,
            payload,
            ReceivingUtils.enrichKafkaHeaderForRapidRelayer(headers, correlationId));
      } else {
        Message<String> message =
            KafkaHelper.buildKafkaMessage(containerTag, payload, moveTopic, headers);

        secureKafkaTemplate.send(message);
        LOG.info(
            "Secure Kafka: Successfully published the move message = {} to topic = {}",
            payload,
            moveTopic);
      }
    } catch (Exception exception) {
      LOG.error(
          "{} Error in publishing move message for the containerId {} with exception - {}",
          SPLUNK_ALERT,
          containerTag,
          ExceptionUtils.getStackTrace(exception));
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE,
          String.format(KAFKA_NOT_ACCESSIBLE_ERROR_MSG, MOVE_PUBLISH_FLOW));
    }
  }
}
