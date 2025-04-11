package com.walmart.move.nim.receiving.core.message.publisher;

import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@ConditionalOnExpression(ReceivingConstants.ENABLE_EI_KAFKA)
@Component
public class RdcKafkaEIPublisher {

  @Qualifier(ReceivingConstants.EI_KAFKA_TEMPLATE)
  @Autowired
  private KafkaTemplate kafkaTemplate;

  private static final Logger LOG = LoggerFactory.getLogger(RdcKafkaEIPublisher.class);

  /**
   * Publish message to EI Kafka
   *
   * @param key the key of kafka message
   * @param payload the payload of kafka message
   * @param topicName the topicName of kafka message
   * @param headers the headers of kafka message
   */
  public void publishEvent(
      String key, String payload, String topicName, Map<String, Object> headers) {
    try {
      String correlationId =
          Objects.isNull(TenantContext.getCorrelationId())
              ? UUID.randomUUID().toString()
              : TenantContext.getCorrelationId();
      Message<String> message =
          KafkaHelper.createKafkaMessage(key, payload, correlationId, topicName, headers);
      LOG.info("EI Kafka sending to={}, key={}, message={}", topicName, key, message);
      kafkaTemplate.send(message);
      LOG.info("EI Kafka Successfully sent to={}, key={}, message={}", topicName, key, message);
    } catch (Exception exception) {
      LOG.error(
          "{} Error in publishing EI Kafka for the topic={}, key={}, payload={}, stack={}",
          ReceivingConstants.SPLUNK_ALERT,
          topicName,
          key,
          payload,
          ExceptionUtils.getStackTrace(exception));
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE,
          String.format(ReceivingConstants.KAFKA_PUBLISH_ERROR_MSG, topicName));
    }
  }
}
