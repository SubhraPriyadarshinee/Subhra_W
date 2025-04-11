package com.walmart.move.nim.receiving.core.message.publisher;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.SPLUNK_ALERT;

import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.service.RapidRelayerService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmartlabs.hawkshaw.clients.generic.auditor.HawkshawAuditor;
import com.walmartlabs.hawkshaw.clients.generic.enricher.HawkshawEnricher;
import com.walmartlabs.hawkshaw.clients.generic.model.HawkshawBusinessKey;
import com.walmartlabs.hawkshaw.clients.generic.util.HawkshawHelpers;
import com.walmartlabs.hawkshaw.model.avro.CloudEvent;
import com.walmartlabs.hawkshaw.model.avro.HawkshawHeaders;
import java.time.Instant;
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

@Component(ReceivingConstants.KAFKA_HAWKSHAW_PUBLISHER)
public class KafkaHawkshawPublisher {
  private static final Logger LOG = LoggerFactory.getLogger(KafkaHawkshawPublisher.class);

  @SecurePublisher private KafkaTemplate secureKafkaTemplate;
  @Autowired private HawkshawEnricher hawkshawEnricher;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private RapidRelayerService rapidRelayerService;

  /**
   * * This function publish kafka message using SecurePublisher with HawkshawHeaders based on
   * tenant specific flag
   *
   * @param key the key of kafka message
   * @param payload the payload of kafka message
   * @param topicName the topicName of kafka message
   * @param headers the headers of kafka message
   * @param type the class type of payload Example: MoveInfo.class.getName() - >
   *     "com.walmart.move.nim.receiving.core.model.move.MoveInfo"
   */
  public void publishKafkaWithHawkshaw(
      Object key, String payload, String topicName, Map<String, Object> headers, String type)
      throws ReceivingInternalException {
    boolean isHawkshawEnabled =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), ReceivingConstants.HAWKSHAW_ENABLED, false);
    LOG.info(
        "Start:publishing Kafka with Hawkshaw enabled = {} for topic = {} with payload = {}, key = {} and headers = {}",
        isHawkshawEnabled,
        topicName,
        payload,
        key,
        headers);
    HawkshawHeaders hawkshawHeaders = null;
    Message<String> message = null;
    try {
      String correlationId =
          Objects.isNull(TenantContext.getCorrelationId())
              ? UUID.randomUUID().toString()
              : TenantContext.getCorrelationId();
      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          TenantContext.getFacilityNum().toString(),
          ReceivingConstants.OUTBOX_PATTERN_ENABLED,
          false)) {
        rapidRelayerService.produceMessage(
            topicName,
            String.valueOf(key),
            payload,
            ReceivingUtils.enrichKafkaHeaderForRapidRelayer(headers, correlationId));
      } else {
        // add hawkshaw headers when enabled
        if (isHawkshawEnabled) {
          hawkshawHeaders = getHawkshawHeaders(key, payload, type);
          LOG.info("HawkshawHeaders ={} for key={}", hawkshawHeaders.toString(), key);
          headers.put(
              ReceivingConstants.HAWKSHAW_HEADER,
              HawkshawHelpers.getHawkshawHeaderBytes(hawkshawHeaders));
        }

        message = KafkaHelper.createKafkaMessage(key, payload, correlationId, topicName, headers);

        LOG.info(
            "Secure Kafka with Hawkshaw={} sending to={}, key={}, message={}",
            isHawkshawEnabled,
            topicName,
            key,
            message);
        secureKafkaTemplate.send(message);
        LOG.info("Secure Kafka Successfully sent to={}, key={}", topicName, key);
      }
    } catch (Exception e) {
      LOG.error(
          "{}Error in publishing Kafka with Hawkshaw={} for the topic={}, key={}, message={}, stack={}",
          SPLUNK_ALERT,
          isHawkshawEnabled,
          topicName,
          key,
          message,
          ExceptionUtils.getStackTrace(e));
      // log failure to hawkshaw when enabled
      if (isHawkshawEnabled) {
        HawkshawAuditor.handleFailure(hawkshawHeaders, e.getMessage());
      }
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE,
          String.format(ReceivingConstants.KAFKA_PUBLISH_ERROR_MSG, topicName));
    }
  }

  private HawkshawHeaders getHawkshawHeaders(Object kafkaKey, String payload, String type) {
    HawkshawHeaders hawkshawheaders = null;
    final Instant now = Instant.now();
    try {
      final HawkshawBusinessKey key =
          new HawkshawBusinessKey(
              TenantContext.getFacilityCountryCode().toLowerCase(),
              ReceivingConstants.HAWKSHAW_SEGMENT,
              now,
              TenantContext.getCorrelationId());
      final CloudEvent cloudEvent =
          CloudEvent.newBuilder()
              .setId(key.toString())
              .setSource(
                  ReceivingConstants.HAWKSHAW_URN_SOURCE
                      + getContextString(
                          TenantContext.getFacilityCountryCode(),
                          TenantContext.getFacilityNum().toString()))
              .setTime(String.valueOf(now.toEpochMilli()))
              .setType(type)
              .build();

      hawkshawheaders =
          hawkshawEnricher
              .enrichOriginData(payload.toString().getBytes(), cloudEvent, false)
              .orElseThrow(RuntimeException::new);

    } catch (Exception e) {
      LOG.error(
          "Error while getting HawkshawHeader for payLoad - {}, with exception - {}",
          payload,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE,
          String.format(ReceivingConstants.HAWKSHAW_HEADER_ERROR, kafkaKey));
    }
    return hawkshawheaders;
  }

  private static String getContextString(String facilityCountryCode, String facilityNumber) {
    return facilityCountryCode.toUpperCase().concat(facilityNumber);
  }
}
