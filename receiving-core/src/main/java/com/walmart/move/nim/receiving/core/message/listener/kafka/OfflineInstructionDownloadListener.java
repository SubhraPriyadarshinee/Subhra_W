package com.walmart.move.nim.receiving.core.message.listener.kafka;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.EventType;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadMessageDTO;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;

/**
 * This class is Listener for Kafka topic b/w OP and RCV for Offline Receiving flow. Author: s0g0g7u
 */
public class OfflineInstructionDownloadListener {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(OfflineInstructionDownloadListener.class);

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private Gson gson;

  @ManagedConfiguration private AppConfig appConfig;

  @KafkaListener(
      topics = "${offline.instruction.download.topic}",
      errorHandler = "kafkaErrorHandler",
      containerFactory = ReceivingConstants.ATLAS_OFFLINE_SECURED_KAFKA_LISTENER_CONTAINER_FACTORY,
      groupId = "${kafka.consumer.offline.groupid:atlas-receiving-xdkListener-dev}",
      concurrency = "${offline.instruction.download.kafka.consumer.threads:1}")
  @Timed(
      name = "offlineInstructionDownload",
      level1 = "uwms-receiving",
      level2 = "offlineInstructionDownload")
  @ExceptionCounted(
      name = "offlineInstructionDownload-Exception",
      level1 = "uwms-receiving",
      level2 = "offlineInstructionDownload-Exception")
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.MESSAGE,
      flow = "offlineInstructionDownload")
  public void listen(
      @Payload String message, @Headers Map<String, byte[]> kafkaHeaders, Acknowledgment ack) {
    try {
      String facility = new String(kafkaHeaders.get(ReceivingConstants.TENENT_FACLITYNUM));
      Integer facilityNum = Integer.parseInt(facility);
      boolean matched =
          appConfig
              .getInstructionDownloadListenerEnabledFacilities()
              .stream()
              .anyMatch(fac -> fac.intValue() == facilityNum.intValue());
      if (!matched) {
        LOGGER.info(
            "Offline Instruction download listener is not enabled for facility: {}, skipping this message",
            facilityNum);
        return;
      }
      if (StringUtils.isEmpty(message)) {
        LOGGER.error(
            String.format(ReceivingConstants.INVALID_INSTRUCTION_DOWNLOAD_MESSAGE, message));
        return;
      }
      InstructionDownloadMessageDTO instructionDownloadMessageDTO =
          gson.fromJson(message, InstructionDownloadMessageDTO.class);
      instructionDownloadMessageDTO.setHttpHeaders(
          ReceivingUtils.populateInstructionDownloadHeadersFromKafkaHeaders(kafkaHeaders));
      if (!isValidEvent(instructionDownloadMessageDTO)) {
        return;
      }
      LOGGER.info(
          "Received offline instruction download message :{} and headers: {}",
          message,
          instructionDownloadMessageDTO.getHttpHeaders());
      EventProcessor instructionDownloadProcessor =
          tenantSpecificConfigReader.getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.KAFKA_INSTRUCTION_DOWNLOAD_PROCESSOR,
              EventProcessor.class);
      instructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    } catch (Exception exception) {
      LOGGER.error(
          "Exception occurred processing offline instruction download : {}",
          ExceptionUtils.getStackTrace(exception));
    } finally {
      ack.acknowledge(); // manual acknowledgment
      TenantContext.clear();
    }
  }

  /**
   * Validating the event
   *
   * @param instructionDownloadMessageDTO
   * @return
   */
  private boolean isValidEvent(InstructionDownloadMessageDTO instructionDownloadMessageDTO) {
    boolean isValidEvent;
    List<String> eventTypeList =
        instructionDownloadMessageDTO.getHttpHeaders().get(ReceivingConstants.EVENT_TYPE);
    String eventTypeVal =
        org.apache.commons.collections4.CollectionUtils.isNotEmpty(eventTypeList)
            ? eventTypeList.get(0)
            : "";
    EventType eventType = EventType.valueOfEventType(eventTypeVal);
    LOGGER.info("Offline Instruction download event type {}", eventTypeVal);
    if (EventType.UNKNOWN.name().contentEquals(eventType.name())) {
      LOGGER.warn(
          "Offline Instruction download event type {} Invalid, skipping the message", eventTypeVal);
      return false;
    }
    if (CollectionUtils.isEmpty(instructionDownloadMessageDTO.getBlobStorage())) {
      LOGGER.warn("[[xdk] Empty Blob Storage - Automation needed this event for duplicate lpns");
      return true;
    }
    isValidEvent =
        instructionDownloadMessageDTO
            .getBlobStorage()
            .stream()
            .anyMatch(blob -> StringUtils.isNotBlank(blob.getBlobUri()));

    LOGGER.info(
        "Offline Instruction download blob storage details are {} for event type : {}",
        isValidEvent ? "Valid" : "Invalid",
        eventType);
    return isValidEvent;
  }
}
