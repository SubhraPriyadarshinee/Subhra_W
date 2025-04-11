package com.walmart.move.nim.receiving.core.message.publisher;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getCorrelationId;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.SPLUNK_ALERT;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
import com.walmart.move.nim.receiving.core.model.witron.WitronPutawayMessage;
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

@Component
public class KafkaWitronPutawayMessagePublisher implements MessagePublisher<WitronPutawayMessage> {
  private static final Logger LOG =
      LoggerFactory.getLogger(KafkaWitronPutawayMessagePublisher.class);

  @Value("${hawkeye.witron.putaway.topic}")
  private String hawkeyeWitronPutawayTopic;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @SecurePublisher private KafkaTemplate kafkaTemplate;
  @ManagedConfiguration private KafkaConfig kafkaConfig;
  @Autowired private RapidRelayerService rapidRelayerService;

  private Gson gson;

  public KafkaWitronPutawayMessagePublisher() {
    gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  @Override
  public void publish(
      WitronPutawayMessage witronPutawayMessage, Map<String, Object> messageHeader) {
    final String payload = gson.toJson(witronPutawayMessage);
    final String action = witronPutawayMessage.getAction();

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
            hawkeyeWitronPutawayTopic,
            witronPutawayMessage.getTrackingId(),
            payload,
            ReceivingUtils.enrichKafkaHeaderForRapidRelayer(messageHeader, correlationId));
      } else {
        final Message<String> message =
            KafkaHelper.buildKafkaMessage(
                witronPutawayMessage.getTrackingId(),
                payload,
                hawkeyeWitronPutawayTopic,
                messageHeader);
        LOG.info("CorrelationId={} publishing... PUTAWAY to Hawkeye", getCorrelationId());
        kafkaTemplate.send(message);
        LOG.info(
            "CorrelationId={} successfully published PUTAWAY to Hawkeye request={}, action={} to topic={}",
            getCorrelationId(),
            payload,
            action,
            hawkeyeWitronPutawayTopic);
      }
    } catch (Exception exception) {
      LOG.error(
          "{}CorrelationId={}, Error in publishing PUTAWAY to Hawkeye request={}, Headers={} with exception={}",
          SPLUNK_ALERT,
          getCorrelationId(),
          payload,
          messageHeader,
          ExceptionUtils.getStackTrace(exception));
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE,
          String.format(
              ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG,
              ReceivingConstants.HAWKEYE_WITRON_PUTAWAY_PUBLISH_FLOW));
    }
  }
}
