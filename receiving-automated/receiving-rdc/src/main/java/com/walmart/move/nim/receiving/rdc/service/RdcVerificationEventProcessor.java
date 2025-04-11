package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.COUNTRY_CODE_US;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.symbotic.RdcVerificationMessage;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service(ReceivingConstants.RDC_KAFKA_VERIFICATION_EVENT_PROCESSOR)
public class RdcVerificationEventProcessor implements EventProcessor {
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private RdcAutoReceiveService rdcAutoReceiveService;
  private static final Logger LOGGER = LoggerFactory.getLogger(RdcVerificationEventProcessor.class);

  @Override
  public void processEvent(MessageData messageData) {
    RdcVerificationMessage rdcVerificationMessage = (RdcVerificationMessage) messageData;
    HttpHeaders httpHeaders = rdcVerificationMessage.getHttpHeaders();
    String groupType = httpHeaders.getFirst(ReceivingConstants.TENENT_GROUP_TYPE);
    String facilityNumber = httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM);
    String facilityCountryCode = httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE);
    String messageType = httpHeaders.getFirst(ReceivingConstants.MESSAGE_TYPE);
    facilityCountryCode =
        Objects.nonNull(facilityCountryCode)
                && facilityCountryCode.equalsIgnoreCase(COUNTRY_CODE_US)
            ? facilityCountryCode.toUpperCase()
            : facilityCountryCode;
    TenantContext.setFacilityCountryCode(facilityCountryCode);
    if (!appConfig
        .getHawkeyeMessageListenerEnabledFacilities()
        .contains(Integer.valueOf(facilityNumber))) {
      LOGGER.info(
          "Hawkeye message listener is not enabled for facility: {}, skipping this verification message {} {}",
          facilityNumber,
          rdcVerificationMessage,
          httpHeaders);
      return;
    }
    if (Objects.nonNull(groupType)
        && !groupType.contains(ReceivingConstants.RDC_VERIFICATION_GROUPTYPE)) {
      LOGGER.info(
          "Verification message is not a RCV_* event with Group_Type:{} and message: {} {}",
          groupType,
          rdcVerificationMessage,
          httpHeaders);
      return;
    }

    if (rdcVerificationMessage
        .getMessageType()
        .equals(ReceivingConstants.RDC_MESSAGE_TYPE_BYPASS)) {
      LOGGER.info(
          "No action is needed for {} events in FLIB AR, message: {}",
          rdcVerificationMessage.getMessageType(),
          rdcVerificationMessage);
      return;
    }

    if (ReceivingConstants.RDC_MESSAGE_TYPE_UNKNOWN.equals(messageType)) {
      LOGGER.info(
          "No action is needed for {} events in FLIB AR, message: {}",
          messageType,
          rdcVerificationMessage);
      return;
    }

    if (Objects.nonNull(rdcVerificationMessage.getInboundTagId())
        && Boolean.TRUE.equals(rdcVerificationMessage.isPalletReceivedStatus())) {
      LOGGER.info(
          "Pallet status is received for message {} {}", rdcVerificationMessage, httpHeaders);
      return;
    }
    // process receiving flow
    try {
      rdcAutoReceiveService.autoReceiveOnVerificationEvent(rdcVerificationMessage, httpHeaders);
    } catch (ReceivingException e) {
      LOGGER.error(
          "Exception {} occurred while receiving lpn {}",
          e.getMessage(),
          rdcVerificationMessage.getLpn());
    }
  }
}
