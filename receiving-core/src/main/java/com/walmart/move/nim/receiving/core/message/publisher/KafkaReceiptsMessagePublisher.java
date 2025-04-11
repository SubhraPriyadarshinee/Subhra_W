package com.walmart.move.nim.receiving.core.message.publisher;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.RECEIPT_PUBLISH_FLOW;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component(ReceivingConstants.KAFKA_RECEIPT_PUBLISHER)
public class KafkaReceiptsMessagePublisher implements MessagePublisher<ContainerDTO> {

  private static final Logger LOG = LoggerFactory.getLogger(KafkaReceiptsMessagePublisher.class);

  private Gson gson;

  public KafkaReceiptsMessagePublisher() {
    gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  @ManagedConfiguration KafkaConfig kafkaConfig;

  @SecurePublisher private KafkaTemplate secureKafkaTemplate;
  @Autowired private KafkaHawkshawPublisher kafkaHawkshawPublisher;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Value("${atlas.receipts.topic}")
  protected String receiptTopic;

  @Override
  public void publish(ContainerDTO container, Map<String, Object> messageHeader) {
    // Set mandatory headers
    LOG.info(
        "Publishing delivery update message for deliveryNumber {}",
        container.getParentTrackingId());
    messageHeader.put(ReceivingConstants.IDEM_POTENCY_KEY, UUID.randomUUID().toString());
    messageHeader.put(ReceivingConstants.MESSAGE_ID_HEADER, UUID.randomUUID().toString());
    messageHeader.put(ReceivingConstants.CONTAINER_TRACKING_ID, container.getTrackingId());
    if (Objects.isNull(messageHeader.get(ReceivingConstants.WMT_REQ_SOURCE))) {
      messageHeader.put(ReceivingConstants.COMPONENT_ID, ReceivingConstants.ATLAS_RECEIVING);
    } else {
      messageHeader.put(ReceivingConstants.COMPONENT_ID, ReceivingConstants.NGR_RECEIVING);
    }

    // publish message
    publishMessageToKafka(container.getTrackingId(), container, messageHeader);
  }

  private void publishMessageToKafka(
      String containerTagId, ContainerDTO containerDTO, Map<String, Object> headers) {
    try {
      LOG.info(
          "Publishing Container message for container {} to kafka topic {}",
          containerTagId,
          receiptTopic);
      String payload =
          tenantSpecificConfigReader.isFeatureFlagEnabled(
                  ReceivingConstants.PUBLISH_SINGLE_CONTAINER_AS_LIST)
              ? gson.toJson(Collections.singletonList(containerDTO))
              : gson.toJson(containerDTO);
      kafkaHawkshawPublisher.publishKafkaWithHawkshaw(
          containerTagId, payload, receiptTopic, headers, ContainerDTO.class.getName());
      LOG.info(
          "Secure Kafka:Successfully published the Container details = {} to topic = {}",
          payload,
          receiptTopic);
    } catch (Exception exception) {
      LOG.error(
          "Secure Kafka:Error in publishing Container details for the containerTagId {} with exception - {}",
          containerTagId,
          ExceptionUtils.getStackTrace(exception));
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE,
          String.format(KAFKA_NOT_ACCESSIBLE_ERROR_MSG, RECEIPT_PUBLISH_FLOW));
    }
  }
}
