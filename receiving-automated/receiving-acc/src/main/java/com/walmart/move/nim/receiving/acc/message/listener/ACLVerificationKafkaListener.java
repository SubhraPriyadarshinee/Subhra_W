package com.walmart.move.nim.receiving.acc.message.listener;

import com.walmart.move.nim.receiving.acc.model.acl.verification.ACLVerificationEventMessage;
import com.walmart.move.nim.receiving.acc.model.acl.verification.ACLVerificationPayload;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;

public class ACLVerificationKafkaListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(ACLVerificationKafkaListener.class);

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  ACLVerificationPayload aclVerificationEventMessages;

  @KafkaListener(
      topics = "${acl.kafka.verification.topic}",
      errorHandler = "kafkaErrorHandler",
      containerFactory = ReceivingConstants.HAWKEYE_SECURE_KAFKA_LISTENER_CONTAINER_FACTORY,
      groupId = "#{'${kafka.consumer.groupid:receiving-consumer}'.concat('-hawkeye-acl-verify')}")
  @Timed(
      name = "consumeACLVerificationKafka",
      level1 = "uwms-receiving",
      level2 = "consumeACLVerificationKafka")
  @ExceptionCounted(
      name = "consumeACLVerificationKafka-Exception",
      level1 = "uwms-receiving",
      level2 = "consumeACLVerificationKafka-Exception")
  @TimeTracing(component = AppComponent.ACC, type = Type.MESSAGE, flow = "ACLVerificationScan")
  public void listen(@Payload String message, @Headers Map<String, byte[]> kafkaHeaders) {
    LOGGER.info("ACL Verification Kafka message:{}", message);
    processListener(message);
    TenantContext.clear();
  }

  public void processListener(String message) {

    if (StringUtils.isEmpty(message)) {
      LOGGER.error("Message format on ACLVerification Listener is null or empty");
      return;
    }

    try {
      LOGGER.info("Got the data on verification queue ----> {}", message);

      aclVerificationEventMessages =
          JacksonParser.convertJsonToObject(message, ACLVerificationPayload.class);

      if (aclVerificationEventMessages != null
          && aclVerificationEventMessages.getLabelVerificationAck().size() > 0) {
        ACLVerificationEventMessage aclVerificationEventMessage =
            aclVerificationEventMessages.getLabelVerificationAck().get(0);
        EventProcessor aclVerificationEventProcessor =
            tenantSpecificConfigReader.getConfiguredInstance(
                TenantContext.getFacilityNum().toString(),
                ReceivingConstants.ACL_VERIFICATION_PROCESSOR,
                EventProcessor.class);
        aclVerificationEventProcessor.processEvent(aclVerificationEventMessage);

        LOGGER.info(
            "Successfully consumed ACL Verification for delivery using Kafka:{}",
            aclVerificationEventMessage.getLpn()
                + " Group Number: "
                + aclVerificationEventMessage.getGroupNbr());
      } else {
        LOGGER.info("No data on verification queue ----> {}", message);
      }
    } catch (Exception excp) {
      LOGGER.error(
          "Unable to process ACL Verification Kafka message - {}",
          ExceptionUtils.getStackTrace(excp));
      throw new ReceivingInternalException(
          ExceptionCodes.ACL_ERROR,
          String.format(ReceivingConstants.UNABLE_TO_PROCESS_NOTIFICATION_ERROR_MSG, message),
          excp);
    }
  }
}
