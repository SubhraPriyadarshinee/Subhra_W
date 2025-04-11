package com.walmart.move.nim.receiving.core.message.publisher;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
import com.walmart.move.nim.receiving.core.model.PublishInstructionSummary;
import com.walmart.move.nim.receiving.core.service.RapidRelayerService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
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

@Component(ReceivingConstants.KAFKA_INSTRUCTION_PUBLISHER)
public class KafkaInstructionMessagePublisher extends InstructionPublisher
    implements MessagePublisher<PublishInstructionSummary> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(KafkaInstructionMessagePublisher.class);

  @ManagedConfiguration private KafkaConfig kafkaConfig;
  @SecurePublisher private KafkaTemplate kafkaTemplate;
  @Autowired private RapidRelayerService rapidRelayerService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Value("${receiving.instruction.topic}")
  private String instructionTopic;

  @Override
  public void publish(
      PublishInstructionSummary publishInstructionSummary, Map<String, Object> messageHeader) {
    try {
      String payload = gson.toJson(publishInstructionSummary);
      String correlationId =
          Objects.isNull(TenantContext.getCorrelationId())
              ? UUID.randomUUID().toString()
              : TenantContext.getCorrelationId();
      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          TenantContext.getFacilityNum().toString(),
          ReceivingConstants.OUTBOX_PATTERN_ENABLED,
          false)) {
        rapidRelayerService.produceMessage(
            instructionTopic,
            getKafkaKey(publishInstructionSummary).toString(),
            payload,
            ReceivingUtils.enrichKafkaHeaderForRapidRelayer(messageHeader, correlationId));
      } else {
        Message<String> message =
            KafkaHelper.buildKafkaMessage(
                getKafkaKey(publishInstructionSummary), payload, instructionTopic, messageHeader);

        kafkaTemplate.send(message);
        LOGGER.info(
            "Secure Kafka : Successfully sent the instruction  = {} on topic = {}",
            payload,
            instructionTopic);
      }
    } catch (Exception exception) {
      LOGGER.error(
          "{} Unable to publish instruction {}",
          SPLUNK_ALERT,
          ExceptionUtils.getStackTrace(exception));
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE,
          String.format(KAFKA_NOT_ACCESSIBLE_ERROR_MSG, INSTRUCTION_PUBLISH_FLOW));
    }
  }
}
