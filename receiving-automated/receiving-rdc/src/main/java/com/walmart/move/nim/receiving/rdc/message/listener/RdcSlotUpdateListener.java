package com.walmart.move.nim.receiving.rdc.message.listener;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.symbotic.RdcSlotUpdateMessage;
import com.walmart.move.nim.receiving.rdc.service.RdcSlotUpdateEventProcessor;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.*;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.util.ObjectUtils;

public class RdcSlotUpdateListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(RdcSlotUpdateListener.class);
  @Autowired private Gson gson;
  @Autowired private RdcSlotUpdateEventProcessor rdcSlotUpdateEventProcessor;
  @ManagedConfiguration AppConfig appConfig;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @KafkaListener(
      topics = "${rdc.kafka.slot.update.topic}",
      errorHandler = "kafkaErrorHandler",
      containerFactory = ReceivingConstants.ATLAS_SECURED_KAFKA_LISTENER_CONTAINER_FACTORY,
      groupId = "${rdc.slot.update.kafka.consumer.groupid:receiving-consumer}",
      concurrency = "${rdc.slot.update.kafka.consumer.threads:1}")
  @Timed(
      name = "rdcConsumeSlotUpdateMessage",
      level1 = "uwms-receiving",
      level2 = "rdcConsumeSlotUpdateMessage")
  @ExceptionCounted(
      name = "rdcConsumeSlotUpdateMessage-Exception",
      level1 = "uwms-receiving",
      level2 = "rdcConsumeSlotUpdateMessage-Exception")
  @TimeTracing(
      component = AppComponent.RDC,
      type = Type.MESSAGE,
      flow = "rdcConsumeSlotUpdateMessage")
  public void listen(@Payload String message, @Headers Map<String, byte[]> kafkaHeaders) {
    LOGGER.info("Received slot update message from smart slotting, message: {}", message);
    if (ObjectUtils.isEmpty(message)) {
      LOGGER.error(ReceivingConstants.WRONG_SLOT_UPDATE_MESSAGE_FORMAT, message);
      return;
    }
    try {
      HttpHeaders httpHeaders = ReceivingUtils.populateHttpHeadersFromKafkaHeaders(kafkaHeaders);
      if (isValidSlotUpdateEvent(httpHeaders) && isValidSlotUpdateEventType(httpHeaders)) {
        List<RdcSlotUpdateMessage> rdcSlotUpdateMessageList =
            Arrays.asList(gson.fromJson(message, RdcSlotUpdateMessage[].class));
        if (CollectionUtils.isEmpty(rdcSlotUpdateMessageList)) {
          LOGGER.error("Slot update message from smart slotting is empty");
          return;
        }
        RdcSlotUpdateMessage rdcSlotUpdateMessage = rdcSlotUpdateMessageList.get(0);
        rdcSlotUpdateMessage.setHttpHeaders(
            ReceivingUtils.populateHttpHeadersFromKafkaHeaders(kafkaHeaders));
        rdcSlotUpdateEventProcessor.processEvent(rdcSlotUpdateMessage);
      }
    } catch (Exception excp) {
      throw new ReceivingInternalException(
          ExceptionCodes.SYMBOTIC_ERROR,
          String.format(ReceivingConstants.UNABLE_TO_PROCESS_SLOT_UPDATE_EVENT_ERROR_MSG, message),
          excp);
    }
  }

  private boolean isValidSlotUpdateEvent(HttpHeaders headers) {
    Integer facilityNum =
        Integer.parseInt(
            Objects.requireNonNull(headers.get(ReceivingConstants.TENENT_FACLITYNUM)).get(0));
    if (!(appConfig.getSlotUpdateListenerEnabledFacilities().contains(facilityNum))) {
      LOGGER.info(
          "Slot Update Listener is not enabled for facility: {}, skipping this message",
          facilityNum);
      return false;
    }
    if (!tenantSpecificConfigReader.getConfiguredFeatureFlag(
        String.valueOf(TenantContext.getFacilityNum()),
        ReceivingConstants.RDC_SSTK_LABEL_GENERATION_ENABLED,
        false)) {
      LOGGER.info(
          "SSTK Label generation disabled for facility: {} , skipping this message", facilityNum);
      return false;
    }
    return true;
  }

  private boolean isValidSlotUpdateEventType(HttpHeaders headers) {
    List<String> eventTypeList = headers.get(ReceivingConstants.EVENT_TYPE);
    String eventType =
        CollectionUtils.isNotEmpty(eventTypeList)
            ? eventTypeList.get(0)
            : ReceivingConstants.EMPTY_STRING;
    return eventType.equals(ReceivingConstants.ITEM_PRIME_DETAILS)
        || eventType.equals(ReceivingConstants.ITEM_PRIME_DELETE);
  }
}
