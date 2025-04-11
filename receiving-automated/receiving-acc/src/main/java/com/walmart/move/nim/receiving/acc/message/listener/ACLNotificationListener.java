package com.walmart.move.nim.receiving.acc.message.listener;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.sanitize;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.acc.model.acl.notification.ACLNotification;
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
 * Listens for messages from the ACL Notification Queue and passes them to the handler
 *
 * @author r0s01us
 */
public class ACLNotificationListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(ACLNotificationListener.class);
  @Autowired private Gson gson;

  @Autowired TenantSpecificConfigReader tenantSpecificConfigReader;

  @JmsListener(destination = "${queue.acl.notification}", containerFactory = "receivingJMSListener")
  public void listen(Message message, MessageHeaders messageHeaders)
      throws JMSException, ReceivingException {
    LOGGER.info("ACL Notification headers:{}", messageHeaders);
    LOGGER.info("ACL Notification message:{}", message);
    ReceivingUtils.setContextFromMsgHeaders(messageHeaders, this.getClass().getName());
    processListener(message);
    TenantContext.clear();
  }

  private void processListener(Message message) throws JMSException, ReceivingException {
    if (!(message instanceof TextMessage)) {
      LOGGER.error("Message format on ACLNotification Listener is wrong");
      return;
    }

    String aclMessage = sanitize(((TextMessage) message).getText());
    LOGGER.debug("Got the data on notification queue ----> {}", aclMessage);
    ACLNotification aclNotification = gson.fromJson(aclMessage, ACLNotification.class);
    EventProcessor aclNotificationEventProcessor =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ACL_NOTIFICATION_PROCESSOR,
            EventProcessor.class);
    aclNotificationEventProcessor.processEvent(aclNotification);
  }
}
