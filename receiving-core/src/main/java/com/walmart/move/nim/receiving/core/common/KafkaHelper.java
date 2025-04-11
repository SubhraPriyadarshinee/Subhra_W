package com.walmart.move.nim.receiving.core.common;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getUserId;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static java.util.Objects.nonNull;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import org.apache.commons.collections4.MapUtils;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

public class KafkaHelper {

  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaHelper.class);
  private static final String KAFKA_HEADERS_ADDITIONAL_PARAM = "kafkaHeaders:";

  public static Message<String> buildKafkaMessage(Object key, String payload, String topicName) {
    return buildKafkaMessage(key, payload, topicName, null);
  }

  public static Message<String> buildKafkaMessage(
      Object key, String payload, String topicName, Map<String, Object> headers) {

    if (Objects.isNull(payload)) {
      throw new ReceivingInternalException(
          ExceptionCodes.INVALID_KAFKA_PAYLOAD, "MessageBody cannot be null ");
    }
    String correlationId =
        Objects.isNull(TenantContext.getCorrelationId())
            ? UUID.randomUUID().toString()
            : TenantContext.getCorrelationId();

    Message<String> message = createKafkaMessage(key, payload, correlationId, topicName, headers);

    LOGGER.info(
        "CorrelationId for kafka is set to {} for topic = {} with payload = {} and headers = {}",
        correlationId,
        topicName,
        payload,
        headers);

    return message;
  }

  public static Message<String> createKafkaMessage(
      Object kafkaKey,
      String payload,
      String correlationId,
      String topic,
      Map<String, Object> headers) {

    if (Objects.isNull(kafkaKey)) {
      throw new ReceivingInternalException(
          ExceptionCodes.INVALID_KAFKA_PAYLOAD, "Key Cannot be null");
    }

    // create Kafka Headers
    MessageHeaderAccessor accessor = new MessageHeaderAccessor();
    accessor.setHeader(KafkaHeaders.TOPIC, topic);
    accessor.setHeader(KafkaHeaders.MESSAGE_KEY, kafkaKey.toString());

    if (MapUtils.isNotEmpty(headers)) {
      headers.forEach(
          (key, value) -> {
            if (value instanceof byte[]) accessor.setHeader(key, value);
            else accessor.setHeader(key, value.toString().getBytes());
          });
    }

    if (MapUtils.isNotEmpty(TenantContext.getAdditionalParams())) {
      TenantContext.getAdditionalParams()
          .forEach(
              (key, value) -> {
                if (key.contains(KAFKA_HEADERS_ADDITIONAL_PARAM)) {
                  String header = key.replace(KAFKA_HEADERS_ADDITIONAL_PARAM, "");
                  accessor.setHeader(key, value.toString().getBytes());
                  LOGGER.info("Kafka Additional header added = {} ", header);
                }
              });
    }

    if (Objects.isNull(accessor.getHeader(ReceivingConstants.TENENT_FACLITYNUM))
        || Objects.isNull(accessor.getHeader(ReceivingConstants.TENENT_COUNTRY_CODE))) {
      accessor.setHeader(ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum());
      accessor.setHeader(
          ReceivingConstants.TENENT_COUNTRY_CODE,
          TenantContext.getFacilityCountryCode().getBytes());
    }

    if (Objects.isNull(accessor.getHeader(ReceivingConstants.CORRELATION_ID_HEADER_KEY))) {
      accessor.setHeader(ReceivingConstants.CORRELATION_ID_HEADER_KEY, correlationId.getBytes());
    }
    accessor.setHeader(ReceivingConstants.API_VERSION, ReceivingConstants.API_VERSION_VALUE);
    accessor.setHeader(
        ReceivingConstants.REQUEST_ORIGINATOR, ReceivingConstants.APP_NAME_VALUE.getBytes());
    accessor.setHeader(USER_ID_HEADER_KEY, getUserId());

    if (nonNull(headers) && nonNull(headers.get(SUBCENTER_ID_HEADER))) {
      accessor.setHeader(SUBCENTER_ID_HEADER, headers.get(SUBCENTER_ID_HEADER).toString());
    }
    if (nonNull(headers) && nonNull(headers.get(ORG_UNIT_ID_HEADER))) {
      accessor.setHeader(ORG_UNIT_ID_HEADER, headers.get(ORG_UNIT_ID_HEADER).toString());
    }

    // get MessageHeaders from accessor
    final MessageHeaders messageHeaders = accessor.getMessageHeaders();

    // create Message
    return MessageBuilder.createMessage(payload, messageHeaders);
  }

  public static void setKafkaSecurityProps(
      Map<String, Object> property,
      KafkaConfig kafkaConfig,
      String secretKey,
      boolean isWCNPDeployable)
      throws IllegalBlockSizeException, InvalidKeyException, BadPaddingException,
          NoSuchAlgorithmException, NoSuchPaddingException {
    String trustStoreLocation =
        isWCNPDeployable
            ? kafkaConfig.getKafkaSSLTruststoreWcnpLocation()
            : kafkaConfig.getKafkaSSLTruststoreLocation();

    String keyStoreLocation =
        isWCNPDeployable
            ? kafkaConfig.getKafkaSSLKeystoreWcnpLocation()
            : kafkaConfig.getKafkaSSLKeystoreLocation();

    LOGGER.info(
        "Kafka SSL TrustStore location: {} and KeyStore location: {}",
        trustStoreLocation,
        keyStoreLocation);

    property.put(
        CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, kafkaConfig.getKafkaSecurityProtocol());
    property.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, trustStoreLocation);
    property.put(
        SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG,
        SecurityUtil.decryptValue(secretKey, kafkaConfig.getKafkaSSLTruststorePassword()));
    property.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, keyStoreLocation);
    property.put(
        SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG,
        SecurityUtil.decryptValue(secretKey, kafkaConfig.getKafkaSSLKeystorePassword()));
    property.put(
        SslConfigs.SSL_KEY_PASSWORD_CONFIG,
        SecurityUtil.decryptValue(secretKey, kafkaConfig.getKafkaSSLKeyPassword()));
  }

  public static void setKafkaPropsForTaas(
      Map<String, Object> property,
      KafkaConfig kafkaConfig,
      boolean isWCNPDeployable,
      boolean isTaasKafka) {
    if (isWCNPDeployable && isTaasKafka) {
      String trustStoreLocation = kafkaConfig.getTaasKafkaSSLTruststoreWcnpLocation();
      String keyStoreLocation = kafkaConfig.getTaasKafkaSSLKeystoreWcnpLocation();

      property.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, trustStoreLocation);
      property.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, keyStoreLocation);
    }
  }
}
