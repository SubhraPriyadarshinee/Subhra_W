package com.walmart.move.nim.receiving.acc.message.listener;

import com.walmart.move.nim.receiving.acc.model.acl.verification.ACLVerificationEventMessage;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.MessageHeaders;

/**
 * Listens for messages from the ACL Verification Queue and passes them to the handler
 *
 * @author r0s01us
 */
public class ACLVerificationListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(ACLVerificationListener.class);

  @Autowired TenantSpecificConfigReader tenantSpecificConfigReader;

  @JmsListener(destination = "${queue.acl.verification}", containerFactory = "receivingJMSListener")
  @TimeTracing(component = AppComponent.ACC, type = Type.MESSAGE, flow = "ACLVerificationScan")
  public void listen(Message message, MessageHeaders messageHeaders)
      throws JMSException, ReceivingException {
    LOGGER.info("ACL Verification headers:{}", messageHeaders);
    LOGGER.info("ACL Verification message:{}", message);
    ReceivingUtils.setContextFromMsgHeaders(messageHeaders, this.getClass().getName());
    processListener(message);
    TenantContext.clear();
  }

  public void processListener(Message message) throws JMSException, ReceivingException {
    if (!(message instanceof TextMessage)) {
      LOGGER.error("Message format on ACLVerification Listener is wrong");
      return;
    }

    String aclMessage = ((TextMessage) message).getText();
    LOGGER.info("Got the data on verification queue ----> {}", aclMessage);
    ACLVerificationEventMessage aclVerificationEventMessage =
        JacksonParser.convertJsonToObject(aclMessage, ACLVerificationEventMessage.class);
    EventProcessor aclVerificationEventProcessor =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ACL_VERIFICATION_PROCESSOR,
            EventProcessor.class);
    aclVerificationEventProcessor.processEvent(aclVerificationEventMessage);
  }
}
