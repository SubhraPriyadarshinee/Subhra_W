package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.SPLUNK_ALERT;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.client.relayer.RapidRelayerClient;
import com.walmart.move.nim.receiving.core.common.RapidRelayerDataMapper;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RapidRelayerService {
  private static final Logger LOGGER = LoggerFactory.getLogger(RapidRelayerService.class);

  @ManagedConfiguration AppConfig appConfiguration;

  @Autowired private RapidRelayerClient rapidRelayerClient;
  private final Gson gson = new Gson();
  private final Type type = new TypeToken<Map<String, String>>() {}.getType();

  public void produceMessage(
      String topic, String msgKey, String msgBody, Map<String, Object> headers)
      throws ReceivingException {
    LOGGER.info(
        "Sending Kafka message through outbox pattern implementation, topic: {}, key: {}, value: {}",
        topic,
        msgKey,
        msgBody);
    if (StringUtils.isEmpty(appConfiguration.getOutboxPatternPublisherPolicyIds())) {
      LOGGER.error(
          "{}Publisher policy ID not configured for the topic: {}, in CCM", SPLUNK_ALERT, topic);
      throw new ReceivingException(
          "KAFKA_PUBLISHER_POLICY_ID_NOT_CONFIGURED", HttpStatus.INTERNAL_SERVER_ERROR);
    }
    Map<String, String> topicAndPublisherPolicyMap =
        gson.fromJson(appConfiguration.getOutboxPatternPublisherPolicyIds(), type);
    String publisherPolicyId = topicAndPublisherPolicyMap.get(topic);
    if (StringUtils.isEmpty(publisherPolicyId)) {
      LOGGER.error(
          "{}Publisher policy ID not configured for the topic: {}, in CCM", SPLUNK_ALERT, topic);
      throw new ReceivingException(
          "KAFKA_PUBLISHER_POLICY_ID_NOT_CONFIGURED", HttpStatus.INTERNAL_SERVER_ERROR);
    }
    rapidRelayerClient.sendDataToRapidRelayer(
        RapidRelayerDataMapper.mapKafkaPublisherRequestoRapidRelayerData(
            topic,
            publisherPolicyId,
            Optional.ofNullable(msgKey).map(Object::toString).orElse(null),
            msgBody,
            headers));
    LOGGER.info(
        "Secure Kafka:Successfully published using OutboxPattern to topic = {} key: {}",
        topic,
        msgKey);
  }

  public void produceHttpMessage(String flowName, String msgBody, Map<String, Object> headers)
      throws ReceivingException {
    LOGGER.info("Sending HTTP message through outbox pattern implementation, msgBody: {}", msgBody);

    if (StringUtils.isEmpty(appConfiguration.getOutboxPatternPublisherPolicyIds())) {
      LOGGER.error(
          "{} Publisher policy ID not configured for the flowName: {}, in CCM",
          SPLUNK_ALERT,
          flowName);
      throw new ReceivingException(
          "HTTP_PUBLISHER_POLICY_ID_NOT_CONFIGURED", HttpStatus.INTERNAL_SERVER_ERROR);
    }
    Map<String, String> topicAndPublisherPolicyMap =
        gson.fromJson(appConfiguration.getOutboxPatternPublisherPolicyIds(), type);
    String publisherPolicyId = topicAndPublisherPolicyMap.get(flowName);
    if (StringUtils.isEmpty(publisherPolicyId)) {
      LOGGER.error(
          "{} Publisher policy ID not configured for the flowName: {}, in CCM",
          SPLUNK_ALERT,
          flowName);
      throw new ReceivingException(
          "HTTP_PUBLISHER_POLICY_ID_NOT_CONFIGURED", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    rapidRelayerClient.sendDataToRapidRelayer(
        RapidRelayerDataMapper.mapHttpPublisherRequestoRapidRelayerData(
            publisherPolicyId, msgBody, headers));
    LOGGER.info(
        "HTTP:Successfully published using OutboxPattern to flowName = {} msgBody: {}",
        flowName,
        msgBody);
  }
}
