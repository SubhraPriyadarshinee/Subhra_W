package com.walmart.move.nim.receiving.mfc.message;

import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.UNDERSCORE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DELIM_DASH;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.ngr.NGRPack;
import com.walmart.move.nim.receiving.mfc.config.MFCManagedConfig;
import com.walmart.move.nim.receiving.mfc.entity.DecantAudit;
import com.walmart.move.nim.receiving.mfc.model.common.AuditStatus;
import com.walmart.move.nim.receiving.mfc.processor.StoreNGRFinalizationEventProcessor;
import com.walmart.move.nim.receiving.mfc.service.DecantAuditService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;

public class StoreNGRFinalizationEventListener {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(StoreNGRFinalizationEventListener.class);

  @Autowired private Gson gson;

  @ManagedConfiguration private MFCManagedConfig mfcManagedConfig;

  @ManagedConfiguration private AppConfig appConfig;

  @Autowired private StoreNGRFinalizationEventProcessor storeNgrFinalizationEventProcessor;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Autowired private DecantAuditService decantAuditService;

  @Timed(
      name = "ngrFinalizationTimed",
      level1 = "uwms-receiving-api",
      level2 = "ngrFinalizationEventListener")
  @ExceptionCounted(
      name = "ngrFinalizationCount",
      level1 = "uwms-receiving-api",
      level2 = "ngrFinalizationEventListener")
  @KafkaListener(
      topics = "${store.ngr.finalization.event.topic}",
      errorHandler = "kafkaErrorHandler",
      containerFactory = ReceivingConstants.STORE_NGR_SECURE_KAFKA_LISTENER_CONTAINER_FACTORY)
  public void listen(@Payload String message, @Headers Map<String, byte[]> kafkaHeaders) {
    if (StringUtils.isEmpty(message)) {
      LOGGER.error(ReceivingConstants.WRONG_NGR_FINALIZATION_LISTENER_MESSAGE_FORMAT, message);
      return;
    }
    LOGGER.info("Entering to NGRFinalizationEventListener with message {}", message);
    AuditStatus auditStatus = null;
    NGRPack finalizedPack = null;
    DecantAudit decantAudit = null;
    boolean isEventEligibleForProcessing = false;
    try {
      finalizedPack = gson.fromJson(message, NGRPack.class);
      setTenantContext(finalizedPack);
      if (!isValidSite()) {
        LOGGER.info(
            "NGR finalization event not allowed for facilityNum {}. Hence skipping the flow",
            TenantContext.getFacilityNum());
        return;
      }

      if (!isEventProcessable(finalizedPack)) {
        LOGGER.info(
            "NGR finalization event not allowed for documentType {} and delivery Type {}. Hence skipping the flow",
            finalizedPack.getDocumentType(),
            finalizedPack.getReceivingDeliveryType());
        return;
      }

      String idempotencyKey =
          new StringBuilder()
              .append(finalizedPack.getReceivingDeliveryId())
              .append(DELIM_DASH)
              .append(finalizedPack.getInboundDocumentPackId())
              .toString();
      decantAudit = decantAuditService.findByCorrelationId(idempotencyKey).orElse(null);

      if (Objects.nonNull(decantAudit)
          && AuditStatus.getInvalidStatusForReprocessing().contains(decantAudit.getStatus())) {
        LOGGER.info(
            "NGR finalization event is duplicate for documentType {} and delivery Type {}. Hence skipping the flow",
            finalizedPack.getDocumentType(),
            finalizedPack.getReceivingDeliveryType());
        return;
      }
      decantAudit = createAuditRecordIfRequired(decantAudit, idempotencyKey, finalizedPack);
      isEventEligibleForProcessing = true;
      storeNgrFinalizationEventProcessor.processEvent(finalizedPack);
      auditStatus = AuditStatus.SUCCESS;
      LOGGER.info("Successfully consumed NGR Finalization event");
    } catch (JsonParseException exception) {
      LOGGER.error(
          "Exception occurred while parsing ngr finalization event message: {}",
          message,
          exception);
      auditStatus = AuditStatus.FAILURE;
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_NGR_EVENT,
          ReceivingConstants.WRONG_NGR_FINALIZATION_LISTENER_MESSAGE_FORMAT);
    } catch (ReceivingBadDataException | ReceivingDataNotFoundException exception) {
      LOGGER.error(
          "Exception occurred while processing ngr finalization event message: {}",
          exception.getMessage(),
          exception);
      auditStatus = AuditStatus.FAILURE;
      throw exception;
    } catch (Exception exception) {
      LOGGER.error(
          "Unable to process NGR Finalization event - {}", exception.getMessage(), exception);
      auditStatus = AuditStatus.FAILURE;
      throw new ReceivingInternalException(
          ExceptionCodes.NGR_EVENT_ERROR,
          String.format(ReceivingConstants.UNABLE_TO_PROCESS_NGR_FINALIZATION_EVENT_MSG, message),
          exception);
    } finally {
      updateDecantAuditStatus(
          auditStatus, finalizedPack, decantAudit, isEventEligibleForProcessing);
      MDC.clear();
    }
    LOGGER.info("Exiting from NGRFinalizationEventListener");
  }

  private DecantAudit createAuditRecordIfRequired(
      DecantAudit decantAudit, String idempotencyKey, NGRPack finalizedPack) {
    if (Objects.isNull(decantAudit)) {
      return decantAuditService.createAuditDataDuringNgrProcess(
          idempotencyKey, finalizedPack, AuditStatus.IN_PROGRESS);
    }
    return decantAudit;
  }

  private void updateDecantAuditStatus(
      AuditStatus auditStatus,
      NGRPack finalizedPack,
      DecantAudit decantAudit,
      boolean isEventEligibleForProcessing) {
    if (isEventEligibleForProcessing
        && Objects.nonNull(finalizedPack)
        && Objects.nonNull(decantAudit)) {
      decantAudit.setStatus(auditStatus);
      decantAuditService.save(decantAudit);
    }
  }

  private boolean isValidSite() {
    return appConfig
        .getStoreNgrFinalizationEventKafkaListenerEnabledFacilities()
        .contains(TenantContext.getFacilityNum());
  }

  private boolean isEventProcessable(NGRPack finalizedPack) {
    return mfcManagedConfig
            .getEligibleDeliveryTypeForNgrFinalizationEvent()
            .contains(finalizedPack.getReceivingDeliveryType())
        && mfcManagedConfig
            .getEligibleDocumentTypeForNgrFinalizationEvent()
            .contains(finalizedPack.getDocumentType());
  }

  private void setTenantContext(NGRPack finalizedPack) {
    TenantContext.clear();
    String facilityNum = finalizedPack.getDestinationNumber();
    String facilityCountryCode = finalizedPack.getDestinationCountryCode();
    if (StringUtils.isEmpty(facilityNum) || StringUtils.isEmpty(facilityCountryCode)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_TENANT,
          String.format(ReceivingConstants.INVALID_TENANT_ERROR_MSG, ReceivingConstants.KAFKA));
    }
    String messageKey =
        new StringBuilder()
            .append(finalizedPack.getReceivingDeliveryId())
            .append(UNDERSCORE)
            .append(finalizedPack.getPackNumber())
            .toString();
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    TenantContext.setFacilityCountryCode(facilityCountryCode);
    TenantContext.setAdditionalParams(
        ReceivingConstants.USER_ID_HEADER_KEY, finalizedPack.getReceivingUserId());
    TenantContext.setCorrelationId(UUID.randomUUID().toString());
    TenantContext.setMessageId(messageKey);
    TenantContext.setMessageIdempotencyId(messageKey);

    MDC.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, TenantContext.getCorrelationId());
    MDC.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNum);
    MDC.put(ReceivingConstants.TENENT_COUNTRY_CODE, facilityCountryCode);
    MDC.put(ReceivingConstants.USER_ID_HEADER_KEY, finalizedPack.getReceivingUserId());
  }
}
