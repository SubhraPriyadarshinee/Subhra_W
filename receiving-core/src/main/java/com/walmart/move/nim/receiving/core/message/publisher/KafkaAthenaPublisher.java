package com.walmart.move.nim.receiving.core.message.publisher;

import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.SorterPublisher;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.model.sorter.LabelType;
import com.walmart.move.nim.receiving.core.model.sorter.ProgramSorterTO;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.time.Instant;
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
public class KafkaAthenaPublisher extends SorterPublisher {
  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaAthenaPublisher.class);

  @SecurePublisher private KafkaTemplate secureKafkaTemplate;
  @ManagedConfiguration private AppConfig appConfig;

  @Autowired TenantSpecificConfigReader tenantSpecificConfigReader;

  @Value("${athena.sorter.topic}")
  private String athenaSorterTopic;

  /**
   * @param container
   * @param labelType
   */
  @Override
  public void publishLabelToSorter(Container container, String labelType) {
    ProgramSorterTO programSorterTO;
    Map<String, Object> messageHeaders =
        ReceivingUtils.getForwardablHeaderWithTenantData(ReceivingUtils.getHeaders());
    messageHeaders.put(ReceivingConstants.EVENT, ReceivingConstants.LPN_CREATE);
    messageHeaders.put(ReceivingConstants.IS_ATLAS_ITEM, ReceivingConstants.Y);
    if (ReceivingConstants.ONE
        < tenantSpecificConfigReader.getSorterContractVersion(TenantContext.getFacilityNum())) {
      enhanceMessageHeaders(messageHeaders, container, labelType);
      programSorterTO = getSorterDivertPayLoadByLabelTypeV2(container, labelType);
    } else {
      programSorterTO = getSorterDivertPayLoadByLabelType(container, labelType);
    }
    String sorterDivertPayloadJson = JacksonParser.writeValueAsString(programSorterTO);

    try {
      Message<String> message =
          KafkaHelper.buildKafkaMessage(
              container.getTrackingId(),
              sorterDivertPayloadJson,
              athenaSorterTopic,
              messageHeaders);
      secureKafkaTemplate.send(message).get();

      LOGGER.info(
          "Successfully published LPN CrossReference: {}, with Header: {}, to Athena topic {}",
          sorterDivertPayloadJson,
          messageHeaders,
          athenaSorterTopic);
    } catch (InterruptedException interruptedException) {
      LOGGER.error(
          "Interrupted exception occurred while processing LPN CrossReference message to Athena. Exception message: {} for Sorter divert payLoad: {}",
          ExceptionUtils.getStackTrace(interruptedException),
          programSorterTO);
      Thread.currentThread().interrupt();
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE, ReceivingConstants.KAFKA_UNABLE_TO_SEND_ERROR_MSG);
    } catch (Exception exception) {
      LOGGER.error(
          "Unable to send LPN CrossReference message to Athena. Exception message: {},with header: {}, for Sorter divert payLoad: {}",
          ExceptionUtils.getStackTrace(exception),
          messageHeaders,
          programSorterTO);
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE, ReceivingConstants.KAFKA_UNABLE_TO_SEND_ERROR_MSG);
    }
  }
  /**
   * This method is used to update headers for Athena Message when the sorter contract version is 2
   *
   * @param messageHeaders
   * @param container
   * @param labelType
   */
  private void enhanceMessageHeaders(
      Map<String, Object> messageHeaders, Container container, String labelType) {
    if (Objects.nonNull(messageHeaders)) {

      if (!Objects.nonNull(messageHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY))) {
        String correlationId = UUID.randomUUID().toString();
        messageHeaders.put(ReceivingConstants.CORRELATION_ID, correlationId);
        LOGGER.warn(" New correlationid:{} has been created for sorterMsg", correlationId);
      } else {
        messageHeaders.put("correlationId", messageHeaders.get("WMT-CorrelationId"));
      }
      if (!Objects.nonNull(container.getMessageId())) {
        String messageId = UUID.randomUUID().toString();
        messageHeaders.put(ReceivingConstants.MESSAGE_ID_HEADER_KEY, messageId);
        LOGGER.warn(" New messageId:{} has been created for sorterMsg", messageId);
      } else {
        messageHeaders.put(ReceivingConstants.MESSAGE_ID_HEADER_KEY, container.getMessageId());
      }
      messageHeaders.put(ReceivingConstants.MSG_TIMESTAMP, String.valueOf(Instant.now()));
      messageHeaders.put(
          ReceivingConstants.VERSION,
          tenantSpecificConfigReader.getSorterContractVersion(TenantContext.getFacilityNum()));
      messageHeaders.put(ReceivingConstants.EVENT_TYPE, "LPN_CREATE");
      if (LabelType.PUT.name().equalsIgnoreCase(labelType)
          || LabelType.DSDC.name().equalsIgnoreCase(labelType)) {
        messageHeaders.put(ReceivingConstants.LABEL_TYPE, labelType);
      } else {
        messageHeaders.put(ReceivingConstants.LABEL_TYPE, LabelType.STORE.name());
      }
    }
  }
}
