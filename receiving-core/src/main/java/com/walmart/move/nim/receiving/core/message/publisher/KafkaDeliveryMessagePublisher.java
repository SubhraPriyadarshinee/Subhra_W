package com.walmart.move.nim.receiving.core.message.publisher;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.atlas.global.config.annotation.EnableInPrimaryRegionNode;
import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.service.RapidRelayerService;
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

/** @author v0k00fe */
@Component(ReceivingConstants.KAFKA_DELIVERY_STATUS_PUBLISHER)
public class KafkaDeliveryMessagePublisher implements MessagePublisher<DeliveryInfo> {

  private static final Logger LOG = LoggerFactory.getLogger(KafkaDeliveryMessagePublisher.class);

  private Gson gson;
  @Autowired private RapidRelayerService rapidRelayerService;
  @ManagedConfiguration AppConfig appConfiguration;

  public KafkaDeliveryMessagePublisher() {
    gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  @ManagedConfiguration KafkaConfig kafkaConfig;

  @SecurePublisher private KafkaTemplate secureKafkaTemplate;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Value("${atlas.delivery.status.topic}")
  protected String deliveryStatusTopic;

  @Override
  public void publish(DeliveryInfo deliveryInfo, Map<String, Object> messageHeaders) {
    // Set mandatory headers
    LOG.info(
        "Publishing delivery update message for deliveryNumber {}",
        deliveryInfo.getDeliveryNumber());
    messageHeaders.put(ReceivingConstants.IDEM_POTENCY_KEY, UUID.randomUUID().toString());
    messageHeaders.put(ReceivingConstants.MESSAGE_ID_HEADER, UUID.randomUUID().toString());
    messageHeaders.put(ReceivingConstants.DELIVERY_NUMBER, deliveryInfo.getDeliveryNumber());
    if (Objects.isNull(messageHeaders.get(ReceivingConstants.WMT_REQ_SOURCE))) {
      messageHeaders.put(ReceivingConstants.COMPONENT_ID, ReceivingConstants.ATLAS_RECEIVING);
    } else {
      messageHeaders.put(ReceivingConstants.COMPONENT_ID, ReceivingConstants.NGR_RECEIVING);
    }

    // publish message
    publishMessageToKafka(deliveryInfo.getDeliveryNumber(), deliveryInfo, messageHeaders);
  }

  private void publishMessageToKafka(
      long deliveryNumber, Object deliveryMessage, Map<String, Object> headers) {
    try {
      LOG.info(
          "Publishing delivery update message for delivery {} to kafka topic {}",
          deliveryNumber,
          deliveryStatusTopic);
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
            deliveryStatusTopic,
            String.valueOf(deliveryNumber),
            payload,
            ReceivingUtils.enrichKafkaHeaderForRapidRelayer(headers, correlationId));
      } else {
        Message<String> message =
            KafkaHelper.buildKafkaMessage(deliveryNumber, payload, deliveryStatusTopic, headers);

        secureKafkaTemplate.send(message);
        LOG.info(
            "Secure Kafka: Successfully published the delivery status message = {} to topic = {}",
            payload,
            deliveryStatusTopic);
      }
    } catch (Exception exception) {
      LOG.error(
          "{}Error in publishing delivery status message for the delivery {} with exception - {}",
          SPLUNK_ALERT,
          deliveryNumber,
          ExceptionUtils.getStackTrace(exception));
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE,
          String.format(KAFKA_NOT_ACCESSIBLE_ERROR_MSG, DELIVERY_STATUS_PUBLISH_FLOW));
    }
  }
}
