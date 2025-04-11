package com.walmart.move.nim.receiving.acc.message.publisher;

import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.SorterPublisher;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.sorter.ProgramSorterTO;
import com.walmart.move.nim.receiving.utils.constants.NoSwapReason;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.SorterExceptionReason;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;

public class KafkaSorterPublisher extends SorterPublisher {
  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaSorterPublisher.class);

  @SecurePublisher private KafkaTemplate securePublisher;

  @Value("${hawkeye.sorter.divert.topic}")
  private String hawkeyeSorterDivertTopic;

  @ManagedConfiguration private AppConfig appConfig;

  @Override
  public void publishException(String lpn, SorterExceptionReason exceptionReason, Date labelDate) {
    // prepare headers
    Map<String, Object> messageHeaders =
        ReceivingUtils.getForwardablHeaderWithTenantData(ReceivingUtils.getHeaders());
    messageHeaders.put(
        ReceivingConstants.EVENT_TYPE, ACCConstants.HAWK_EYE_SORTER_EVENT_TYPE_HEADER_VALUE);
    messageHeaders.put(
        ReceivingConstants.MSG_TIMESTAMP, ReceivingUtils.dateConversionToUTC(new Date()));

    ProgramSorterTO programSorterTO =
        getSorterDivertPayloadForExceptionContainer(lpn, exceptionReason, labelDate);

    try {
      Message<String> message =
          KafkaHelper.buildKafkaMessage(
              lpn,
              JacksonParser.writeValueAsString(programSorterTO),
              hawkeyeSorterDivertTopic,
              messageHeaders);

      securePublisher.send(message).get();

      LOGGER.info(
          "Hawkeye: Successfully published exception container to Sorter {}", programSorterTO);
    } catch (Exception exception) {
      LOGGER.error(
          "Unable to send to hawkeye sorter Exception: {}, Sorter divert {}",
          ExceptionUtils.getStackTrace(exception),
          programSorterTO);
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE, ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG);
    }
  }

  public void publishStoreLabel(Container container) {
    // prepare headers
    Map<String, Object> messageHeaders =
        ReceivingUtils.getForwardablHeaderWithTenantData(ReceivingUtils.getHeaders());
    messageHeaders.put(
        ReceivingConstants.EVENT_TYPE, ACCConstants.HAWK_EYE_SORTER_EVENT_TYPE_HEADER_VALUE);
    messageHeaders.put(
        ReceivingConstants.MSG_TIMESTAMP, ReceivingUtils.dateConversionToUTC(new Date()));

    ProgramSorterTO programSorterTO = getSorterDivertPayloadForStoreLabel(container);

    try {
      Message<String> message =
          KafkaHelper.buildKafkaMessage(
              container.getTrackingId(),
              JacksonParser.writeValueAsString(programSorterTO),
              hawkeyeSorterDivertTopic,
              messageHeaders);
      securePublisher.send(message).get();

      LOGGER.info("Successfully published diversion to Sorter {}", programSorterTO);
    } catch (Exception exception) {
      LOGGER.error(
          "Unable to send to hawkeye sorter Exception: {}, Sorter divert {}",
          ExceptionUtils.getStackTrace(exception),
          programSorterTO);
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE, ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG);
    }
  }

  @Override
  protected ProgramSorterTO getSorterDivertPayloadForStoreLabel(Container consolidatedContainer) {
    // prepare payload
    ProgramSorterTO programSorterTO =
        super.getSorterDivertPayloadForStoreLabel(consolidatedContainer);
    ContainerItem containerItem = consolidatedContainer.getContainerItems().get(0);
    programSorterTO.setItemNumber(containerItem.getItemNumber().intValue());
    programSorterTO.setGroupNumber(consolidatedContainer.getDeliveryNumber().toString());
    programSorterTO.setPoNumber(containerItem.getPurchaseReferenceNumber());
    programSorterTO.setPoType(containerItem.getOutboundChannelMethod());
    programSorterTO.setNoSwapReason(
        Objects.nonNull(containerItem.getImportInd())
                && Boolean.TRUE.equals(containerItem.getImportInd())
            ? NoSwapReason.IMPORTS
            : null);
    return programSorterTO;
  }
}
