package com.walmart.move.nim.receiving.core.handler;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.listener.adapter.RecordFilterStrategy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * * This class is to intercept all the message coming from Kafka and set {@link TenantContext}
 * along with {@link MDC}
 *
 * @author sitakant
 */
@Component
public class KafkaListenerFilter implements RecordFilterStrategy {

  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaListenerFilter.class);

  @Override
  public boolean filter(ConsumerRecord consumerRecord) {
    MDC.clear();
    TenantContext.clear();
    Headers kafkaHeaders = consumerRecord.headers();
    Iterable<Header> countryCode = kafkaHeaders.headers(ReceivingConstants.TENENT_COUNTRY_CODE);
    Iterable<Header> facilityNum = kafkaHeaders.headers(ReceivingConstants.TENENT_FACLITYNUM);
    Iterable<Header> wmtCoRelationId =
        kafkaHeaders.headers(ReceivingConstants.CORRELATION_ID_HEADER_KEY);

    Iterable<Header> wmtCoRelationIdFallback =
        kafkaHeaders.headers(ReceivingConstants.CORRELATION_ID_HEADER_KEY_FALLBACK);

    Iterable<Header> coRelationId = kafkaHeaders.headers(ReceivingConstants.CORRELATION_ID);
    Iterable<Header> userId = kafkaHeaders.headers(ReceivingConstants.USER_ID_HEADER_KEY);
    Iterable<Header> messageIdHeader =
        kafkaHeaders.headers(ReceivingConstants.MESSAGE_ID_HEADER_KEY);
    Iterable<Header> _messageIdempotencyId =
        kafkaHeaders.headers(ReceivingConstants.ATLAS_KAFKA_IDEMPOTENCY);
    Iterable<Header> _1messageIdempotencyId =
        kafkaHeaders.headers(ReceivingConstants.ATLAS_KAFKA_IDEMPOTENCY_FALLBACK);
    Iterable<Header> _eventType = kafkaHeaders.headers(ReceivingConstants.EVENT_TYPE);

    String countryCodeHeader =
        isNull(countryCode) || !countryCode.iterator().hasNext()
            ? null
            : new String(countryCode.iterator().next().value());
    Integer facilityNumHeader =
        isNull(facilityNum) || !facilityNum.iterator().hasNext()
            ? null
            : Integer.valueOf(new String(facilityNum.iterator().next().value()));
    String userIdHeader =
        isNull(userId) || !userId.iterator().hasNext()
            ? null
            : new String(userId.iterator().next().value());
    // Listening the hawkeye corelationId, if not present then looking for walmart corelationId . If
    // both of them are not present , then creating a UUID
    String coRelationIdHeader =
        retrieveCorrelationId(wmtCoRelationId, wmtCoRelationIdFallback, coRelationId);
    String messageId =
        isNull(messageIdHeader) || !messageIdHeader.iterator().hasNext()
            ? null
            : new String(messageIdHeader.iterator().next().value());
    String messageIdempotencyId = getIdempotencyKey(_messageIdempotencyId, _1messageIdempotencyId);
    String eventType =
        isNull(_eventType) || !_eventType.iterator().hasNext()
            ? null
            : new String(_eventType.iterator().next().value());

    setTenant(
        countryCodeHeader,
        facilityNumHeader,
        coRelationIdHeader,
        userIdHeader,
        messageId,
        messageIdempotencyId,
        eventType);
    MDC.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, coRelationIdHeader);
    MDC.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNumHeader.toString());
    MDC.put(ReceivingConstants.TENENT_COUNTRY_CODE, countryCodeHeader);
    MDC.put(ReceivingConstants.USER_ID_HEADER_KEY, userIdHeader);
    LOGGER.info("MDC set for kafka listener");
    return false;
  }

  private String retrieveCorrelationId(
      Iterable<Header> wmtCoRelationId,
      Iterable<Header> wmtCoRelationIdFallback,
      Iterable<Header> coRelationId) {

    String correlationId = null;

    // CorrelationId for Hawkeye
    if (nonNull(coRelationId) && coRelationId.iterator().hasNext()) {
      correlationId = new String(coRelationId.iterator().next().value());
      LOGGER.info(
          "Retrieved {} from header = {}", ReceivingConstants.CORRELATION_ID, correlationId);
      return correlationId;
    }

    // WMT-CorrelationId for atlas components
    if (nonNull(wmtCoRelationId) && wmtCoRelationId.iterator().hasNext()) {
      correlationId = new String(wmtCoRelationId.iterator().next().value());

      LOGGER.info(
          "Retrieved {} from header =  {} ",
          ReceivingConstants.CORRELATION_ID_HEADER_KEY,
          correlationId);
      return correlationId;
    }

    // WMT_CorrelationId for atlas component (to support MaaS based correlationId implementation)
    if (nonNull(wmtCoRelationIdFallback) && wmtCoRelationIdFallback.iterator().hasNext()) {
      correlationId = new String(wmtCoRelationIdFallback.iterator().next().value());
      LOGGER.info(
          "Retrieved {} from header = {}",
          ReceivingConstants.CORRELATION_ID_HEADER_KEY_FALLBACK,
          correlationId);
      return correlationId;
    }
    correlationId = UUID.randomUUID().toString();
    LOGGER.warn(
        "Unable to find correlationId from header and hence, falling back = {} ", correlationId);
    return correlationId;
  }

  private String getIdempotencyKey(
      Iterable<Header> messageIdempotencyId, Iterable<Header> messageIdempotencyId1) {

    if (nonNull(messageIdempotencyId) && messageIdempotencyId.iterator().hasNext()) {
      LOGGER.info(
          "Retrieved idempotency value for {} header ", ReceivingConstants.ATLAS_KAFKA_IDEMPOTENCY);
      return new String(messageIdempotencyId.iterator().next().value());
    }

    if (nonNull(messageIdempotencyId1) && messageIdempotencyId1.iterator().hasNext()) {
      LOGGER.info(
          "Retrieved fallback idempotency value for {} header",
          ReceivingConstants.ATLAS_KAFKA_IDEMPOTENCY_FALLBACK);
      return new String(messageIdempotencyId1.iterator().next().value());
    }

    LOGGER.info("No Idempotency header found in message, setting null");
    return null;
  }

  private void setTenant(
      String countryCode,
      Integer facilityNum,
      String correlationId,
      String userId,
      String messageId,
      String messageIdempotencyId,
      String eventType) {
    if (StringUtils.isEmpty(countryCode) || isNull(facilityNum)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_TENANT,
          String.format(ReceivingConstants.INVALID_TENANT_ERROR_MSG, ReceivingConstants.KAFKA));
    }
    TenantContext.setFacilityNum(facilityNum);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setCorrelationId(correlationId);
    if (nonNull(userId)) {
      TenantContext.setAdditionalParams(ReceivingConstants.USER_ID_HEADER_KEY, userId);
    }
    TenantContext.setMessageId(messageId);
    TenantContext.setMessageIdempotencyId(messageIdempotencyId);
    TenantContext.setEventType(eventType);
  }
}
