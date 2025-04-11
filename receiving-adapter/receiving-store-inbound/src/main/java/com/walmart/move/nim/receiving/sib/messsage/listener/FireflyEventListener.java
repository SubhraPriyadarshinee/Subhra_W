package com.walmart.move.nim.receiving.sib.messsage.listener;

import static com.walmart.move.nim.receiving.sib.utils.Constants.ISO_FORMAT_STRING_REQUEST;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.message.common.FireflyEvent;
import com.walmart.move.nim.receiving.sib.service.FireflyEventProcessor;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;

/** @author j0p00pb */
public class FireflyEventListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(FireflyEventListener.class);
  private Gson gsonIn = new GsonBuilder().setDateFormat(ISO_FORMAT_STRING_REQUEST).create();
  private static final String FLOW = "NGR-FIREFLY";

  @Autowired private FireflyEventProcessor fireflyEventProcessor;

  // registering new consumer group
  @KafkaListener(
      topics = "${firefly.event.topic}",
      errorHandler = "kafkaErrorHandler",
      containerFactory = ReceivingConstants.FIREFLY_SECURE_KAFKA_LISTENER_CONTAINER_FACTORY,
      groupId = "#{'${kafka.consumer.groupid:receiving-consumer}'.concat('-store-adapter')}")
  @Timed(
      name = "consumeFireflyEvent-ngr-receiveContainer",
      level1 = "uwms-receiving",
      level2 = "consumeFireflyEvent-ngr-receiveContainer")
  @ExceptionCounted(
      name = "consumeFireflyEvent-ngr-receiveContainer-Exception",
      level1 = "uwms-receiving",
      level2 = "consumeFireflyEvent-ngr-receiveContainer-Exception")
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.MESSAGE,
      flow = "consumeFireflyEvent-ngr-receiveContainer")
  public void listen(@Payload String message, @Headers Map<String, byte[]> kafkaHeaders) {
    LOGGER.info("Entering into Firefly Event Listener");

    if (StringUtils.isEmpty(message)) {
      LOGGER.error(ReceivingConstants.WRONG_FIREFLY_MESSAGE_FORMAT, message);
      return;
    }

    String newCorrelationId = UUID.randomUUID() + ReceivingConstants.DELIM_DASH + FLOW;

    LOGGER.info(
        "Received message from Firefly, message: {} and processing with new correlationId = {}",
        message,
        newCorrelationId);

    try {
      FireflyEvent fireflyEvent = gsonIn.fromJson(message, FireflyEvent.class);
      if (invalidFireflyEvent(fireflyEvent)) {
        LOGGER.info("Firefly event is invalid - {}", fireflyEvent.toString());
        return;
      }
      setTenantContext(fireflyEvent, newCorrelationId);
      fireflyEventProcessor.doProcessEvent(fireflyEvent);
      LOGGER.info("Successfully consumed Firefly event");
    } catch (Exception exception) {
      LOGGER.error("Unable to process Firefly event - {}", ExceptionUtils.getStackTrace(exception));
      throw new ReceivingInternalException(
          ExceptionCodes.FIREFLY_ERROR,
          String.format(ReceivingConstants.UNABLE_TO_PROCESS_FIREFLY_EVENT_MSG, message),
          exception);
    } finally {
      MDC.clear();
    }
  }

  private void setTenantContext(FireflyEvent fireflyEvent, String newCorrelationId) {
    Integer facilityNum = fireflyEvent.getBusinessUnitNumber();
    String messageKey =
        new StringBuilder()
            .append(fireflyEvent.getAssetId())
            .append(ReceivingConstants.DELIM_DASH)
            .append(fireflyEvent.getAssociationTimeEpoch())
            .append(ReceivingConstants.DELIM_DASH)
            .append(fireflyEvent.getEventName())
            .append(ReceivingConstants.DELIM_DASH)
            .append(fireflyEvent.getBusinessUnitNumber())
            .append(ReceivingConstants.DELIM_DASH)
            .append(fireflyEvent.getEventTime())
            .toString();

    MDC.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, newCorrelationId);
    MDC.put(ReceivingConstants.TENENT_FACILITYNUMBER, String.valueOf(facilityNum));

    TenantContext.setFacilityNum(facilityNum);
    TenantContext.setFacilityCountryCode(ReceivingConstants.COUNTRY_CODE_US);
    TenantContext.setAdditionalParams(
        ReceivingConstants.USER_ID_HEADER_KEY, ReceivingConstants.USER_ID_AUTO_FINALIZED);
    TenantContext.setCorrelationId(newCorrelationId);
    TenantContext.setMessageId(messageKey);
    TenantContext.setMessageIdempotencyId(messageKey);
  }

  private boolean invalidFireflyEvent(FireflyEvent fireflyEvent) {
    return Objects.isNull(fireflyEvent)
        || Objects.isNull(fireflyEvent.getAssetId())
        || Objects.isNull(fireflyEvent.getAssociationTimeEpoch())
        || Objects.isNull(fireflyEvent.getAssociationTime())
        || Objects.isNull(fireflyEvent.getAssetType())
        || Objects.isNull(fireflyEvent.getEventName())
        || Objects.isNull(fireflyEvent.getBusinessUnitNumber())
        || Objects.isNull(fireflyEvent.getBannerCode())
        || Objects.isNull(fireflyEvent.getBannerDesc())
        || Objects.isNull(fireflyEvent.getEventTime());
  }
}
