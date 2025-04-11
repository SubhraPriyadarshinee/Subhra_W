package com.walmart.move.nim.receiving.rdc.service;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.walmart.move.nim.receiving.core.common.SymboticPutawayPublishHelper;
import com.walmart.move.nim.receiving.core.common.SymboticUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.symbotic.SymHawkeyeEventType;
import com.walmart.move.nim.receiving.core.model.symbotic.SymPutawayMessage;
import com.walmart.move.nim.receiving.rdc.model.RdcMessageType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
public class RdcPublishService {

  private static final Logger logger = LoggerFactory.getLogger(RdcPublishService.class);
  @Autowired private Gson gson;
  @Autowired private SymboticPutawayPublishHelper symboticPutawayPublishHelper;
  @ManagedConfiguration private AppConfig appConfig;

  /**
   * This method publish messages to external systems based on messageType header passed in the
   * payload
   *
   * @param message
   * @param httpHeaders
   */
  public void publishMessage(String message, HttpHeaders httpHeaders) {
    String messageType = httpHeaders.getFirst(ReceivingConstants.MESSAGE_TYPE);
    RdcMessageType rdcMessageType = validateMessageType(messageType);

    switch (rdcMessageType) {
      case PUTAWAY:
        String symSystem = httpHeaders.getFirst(ReceivingConstants.SYMBOTIC_SYSTEM);
        publishPutawayMessage(symSystem, httpHeaders, message);
        break;
      default:
        break;
    }
  }

  private RdcMessageType validateMessageType(String messageType) {
    if (StringUtils.isBlank(messageType)) {
      logger.error(ReceivingConstants.INVALID_MESSAGE_TYPE);
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_MESSAGE_TYPE, ReceivingConstants.INVALID_MESSAGE_TYPE);
    }

    RdcMessageType rdcMessageType = RdcMessageType.getRdcMessageType(messageType);
    if (Objects.isNull(rdcMessageType)) {
      logger.error(ReceivingConstants.UNSUPPORTED_MESSAGE_TYPE);
      throw new ReceivingBadDataException(
          ExceptionCodes.UNSUPPORTED_MESSAGE_TYPE, ReceivingConstants.UNSUPPORTED_MESSAGE_TYPE);
    }
    return rdcMessageType;
  }

  /**
   * This method publishes putaway message to hawkeye
   *
   * @param symSystem
   * @param httpHeaders
   * @param message
   */
  private void publishPutawayMessage(String symSystem, HttpHeaders httpHeaders, String message) {
    if (StringUtils.isBlank(symSystem)) {
      logger.error(ReceivingConstants.INVALID_SYM_SYSTEM);
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_SYM_SYSTEM, ReceivingConstants.INVALID_SYM_SYSTEM);
    }

    if (CollectionUtils.isEmpty(appConfig.getValidSymAsrsAlignmentValues())
        || !appConfig.getValidSymAsrsAlignmentValues().contains(symSystem)) {
      logger.error(
          "Given asrsAlignment: {} is not aligned with Symbotic,so putaway message is not published to Hawkeye",
          symSystem);
      return;
    }

    try {
      String correlationId = httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY);
      SymPutawayMessage symPutawayMessage = gson.fromJson(message, SymPutawayMessage.class);

      if (Objects.nonNull(symPutawayMessage)
          && Objects.nonNull(symPutawayMessage.getTrackingId())) {
        Map<String, Object> symMessageHeader =
            SymboticUtils.getSymPutawayMessageHeader(
                httpHeaders, symSystem, SymHawkeyeEventType.PUTAWAY_REQUEST.toString());
        String trackingId = symPutawayMessage.getTrackingId();
        logger.info(
            "Publish putaway message to Hawkeye for trackingId: {}, correlationId: {}",
            trackingId,
            correlationId);
        symboticPutawayPublishHelper.publish(trackingId, symMessageHeader, symPutawayMessage);
      } else {
        logger.error("Partial putaway request message received for payload: {}", message);
        throw new ReceivingBadDataException(
            ExceptionCodes.PARTIAL_PUTAWAY_REQUEST, ReceivingConstants.PARTIAL_PUTAWAY_REQUEST);
      }
    } catch (JsonParseException jsonParseException) {
      logger.error("Exception occurred while parsing putaway request message: {}", message);
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_PUTAWAY_REQUEST, ReceivingConstants.INVALID_PUTAWAY_REQUEST);
    }
  }
}
