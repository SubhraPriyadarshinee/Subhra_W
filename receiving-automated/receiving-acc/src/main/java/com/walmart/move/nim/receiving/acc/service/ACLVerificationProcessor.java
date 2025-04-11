package com.walmart.move.nim.receiving.acc.service;

import com.walmart.move.nim.receiving.acc.model.acl.verification.ACLVerificationEventMessage;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

public class ACLVerificationProcessor implements EventProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ACLVerificationProcessor.class);
  @Autowired private LPNReceivingService lpnReceivingService;

  /**
   * This method will receive a case by trackingId
   *
   * @param messageData
   */
  @Override
  public void processEvent(MessageData messageData) {
    ACLVerificationEventMessage aclVerificationEventMessage =
        (ACLVerificationEventMessage) messageData;
    if (!isValidateMessage(aclVerificationEventMessage)) {
      LOGGER.error(
          "Ignoring ACL Verification message:invalid message {}", aclVerificationEventMessage);
      return;
    }
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      LOGGER.error(
          "Error while processing lpn {} : {}",
          aclVerificationEventMessage.getLpn(),
          ExceptionUtils.getStackTrace(e));
    }
  }

  private boolean isValidateMessage(ACLVerificationEventMessage aclVerificationEventMessage) {
    return !StringUtils.isEmpty(aclVerificationEventMessage.getLpn())
        && !StringUtils.isEmpty(aclVerificationEventMessage.getLocationId())
        && !StringUtils.isEmpty(aclVerificationEventMessage.getGroupNbr())
        && aclVerificationEventMessage.getGroupNbr().matches("\\d*");
  }
}
