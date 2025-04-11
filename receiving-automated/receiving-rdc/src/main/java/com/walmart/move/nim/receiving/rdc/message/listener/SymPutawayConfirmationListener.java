package com.walmart.move.nim.receiving.rdc.message.listener;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.STATUS_COMPLETE;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.model.symbotic.SymPutawayConfirmation;
import com.walmart.move.nim.receiving.core.service.ContainerAdjustmentHelper;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.util.StringUtils;

public class SymPutawayConfirmationListener {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(SymPutawayConfirmationListener.class);
  @Autowired private ContainerPersisterService containerPersisterService;
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private ReceiptService receiptService;
  @Autowired private ContainerAdjustmentHelper containerAdjustmentHelper;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private Gson gson;

  @KafkaListener(
      topics = "${hawkeye.sym.putaway.confirmation.topic}",
      errorHandler = "kafkaErrorHandler",
      containerFactory = ReceivingConstants.HAWKEYE_SECURE_KAFKA_LISTENER_CONTAINER_FACTORY)
  @Timed(
      name = "consumePutawayConfirmation",
      level1 = "uwms-receiving",
      level2 = "consumePutawayConfirmation")
  @ExceptionCounted(
      name = "consumePutawayConfirmation-Exception",
      level1 = "uwms-receiving",
      level2 = "consumePutawayConfirmation-Exception")
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.MESSAGE,
      flow = "consumePutawayConfirmation")
  public void listen(@Payload String message, @Headers Map<String, byte[]> kafkaHeaders) {
    if (StringUtils.isEmpty(message)) {
      LOGGER.error(ReceivingConstants.WRONG_PUTAWAY_CONFIRMATION_MESSAGE_FORMAT, message);
      return;
    }

    LOGGER.info("Received putaway order confirmation from hawkeye, message: {}", message);

    try {
      SymPutawayConfirmation symPutawayConfirmation =
          gson.fromJson(message, SymPutawayConfirmation.class);
      validatePutAwayConfirmationMessage(symPutawayConfirmation, kafkaHeaders);
    } catch (Exception excp) {
      LOGGER.error(
          "Unable to process putaway order confirmation message - {}",
          ExceptionUtils.getStackTrace(excp));
      throw new ReceivingInternalException(
          ExceptionCodes.SYMBOTIC_ERROR,
          String.format(
              ReceivingConstants.UNABLE_TO_PROCESS_PUTAWAY_CONFIRMATION_ERROR_MSG, message),
          excp);
    }
  }

  /**
   * @param symPutawayConfirmation
   * @param kafkaHeaders:
   */
  private void validatePutAwayConfirmationMessage(
      SymPutawayConfirmation symPutawayConfirmation, Map<String, byte[]> kafkaHeaders) {
    String system = new String(kafkaHeaders.get(ReceivingConstants.SYM_SYSTEM_KEY));
    String messageId = new String(kafkaHeaders.get(ReceivingConstants.SYM_MESSAGE_ID_HEADER));
    String eventType = new String(kafkaHeaders.get(ReceivingConstants.SYM_EVENT_TYPE_KEY));
    String messageTS = new String(kafkaHeaders.get(ReceivingConstants.SYM_MSG_TIMESTAMP));
    String facilityNumber = new String(kafkaHeaders.get(ReceivingConstants.TENENT_FACLITYNUM));

    if (!appConfig
        .getHawkeyeMessageListenerEnabledFacilities()
        .contains(Integer.valueOf(facilityNumber))) {
      LOGGER.info(
          "Hawkeye message listener is not enabled for facility: {}, skipping this putaway confirmation message",
          facilityNumber);
      return;
    }

    if (!StringUtils.isEmpty(symPutawayConfirmation.getTrackingId())) {
      if (CollectionUtils.isNotEmpty(symPutawayConfirmation.getErrorDetails())) {
        LOGGER.info(
            "Consumed partial putaway order confirmation from hawkeye for trackingId: {} and with errorDetails: {}",
            symPutawayConfirmation.getTrackingId(),
            symPutawayConfirmation.getErrorDetails());
      } else {
        LOGGER.info(
            "Successfully consumed putaway order confirmation from Hawkeye with system = {}, messageId = {}, eventType = {}, messageTs = {}, facilityNumber = {} for trackingId = {}, status = {}",
            system,
            messageId,
            eventType,
            messageTS,
            facilityNumber,
            symPutawayConfirmation.getTrackingId(),
            symPutawayConfirmation.getStatus());
      }
      updatePutawayStatus(symPutawayConfirmation.getTrackingId());
    } else {
      LOGGER.error(
          "Unable to process putaway order confirmation message. TrackingId: {} was not found in putaway confirmation message",
          symPutawayConfirmation.getTrackingId());
    }
  }

  private void updatePutawayStatus(String trackingId) {
    if (ReceivingUtils.isValidLpn(trackingId)) {
      Container container =
          containerPersisterService.getContainerWithChildContainersExcludingChildContents(
              trackingId);
      if (Objects.nonNull(container)
          && container.getContainerStatus().equals(STATUS_COMPLETE)
          && ReceivingUtils.isValidLpn(trackingId)) {
        container.setContainerStatus(ReceivingConstants.STATUS_PUTAWAY_COMPLETE);
        containerPersisterService.saveContainer(container);
      } else {
        LOGGER.info(
            "No container found for trackingId: {} received from putaway confirmation message",
            trackingId);
      }
    }
  }
}
