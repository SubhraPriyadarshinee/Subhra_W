package com.walmart.move.nim.receiving.acc.message.listener;

import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.model.acl.notification.ACLNotification;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.TenantSpecificBackendConfig;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.util.StringUtils;

public class KafkaNotificationListener {
  private static final Logger LOGGER = LoggerFactory.getLogger((KafkaNotificationListener.class));
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @ManagedConfiguration private TenantSpecificBackendConfig tenantSpecificBackendConfig;

  @KafkaListener(
      topics = "${hawkeye.notification.topic}",
      errorHandler = "kafkaErrorHandler",
      containerFactory = ReceivingConstants.HAWKEYE_SECURE_KAFKA_LISTENER_CONTAINER_FACTORY,
      groupId =
          "#{'${kafka.consumer.groupid:receiving-consumer}'.concat('-hawkeye-equipment-status')}")
  public void listen(
      @Payload String message, @Header(ACCConstants.EQUIPMENT_TYPE) byte[] equipmentType) {
    String anObject = new String(equipmentType);
    if (!(ACCConstants.DOOR_LINE.equals(anObject) || (ACCConstants.FLOOR_LINE.equals(anObject)))) {
      LOGGER.info("Ignoring message for equipmentType {}", equipmentType);
      return;
    }

    if (StringUtils.isEmpty(message)) {
      LOGGER.error(ReceivingConstants.LOG_WRONG_MESSAGE_FORMAT, message);
      return;
    }

    LOGGER.info("Got notification from hawkeye. payload = {} ", message);
    if (!tenantSpecificBackendConfig
        .getNotificationKafkaEnabledFacilities()
        .contains(TenantContext.getFacilityNum())) {
      LOGGER.info("Kafka notification not enable for facility {} ", TenantContext.getFacilityNum());
      return;
    }

    try {
      processListener(message);
      LOGGER.info("Notification event from hawkeye completed");
    } catch (ReceivingException e) {
      LOGGER.error("Unable to process the notification event {}", ExceptionUtils.getStackTrace(e));
      throw new ReceivingInternalException(
          ExceptionCodes.RECEIVING_INTERNAL_ERROR,
          String.format(ReceivingConstants.UNABLE_TO_PROCESS_NOTIFICATION_ERROR_MSG, message),
          e);
    }
    TenantContext.clear();
  }

  private void processListener(String message) throws ReceivingException {

    ACLNotification aclNotification =
        JacksonParser.convertJsonToObject(message, ACLNotification.class);
    EventProcessor aclNotificationEventProcessor =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ACL_NOTIFICATION_PROCESSOR,
            EventProcessor.class);
    aclNotificationEventProcessor.processEvent(aclNotification);
  }
}
