package com.walmart.move.nim.receiving.acc.message.publisher;

import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class JMSDeliveryLinkPublisher {

  private static final Logger LOGGER = LoggerFactory.getLogger(JMSDeliveryLinkPublisher.class);

  @Autowired private JmsPublisher jmsPublisher;

  public void publish(String messageBody, Map<String, Object> headers) {
    ReceivingJMSEvent jmsEvent = new ReceivingJMSEvent(headers, messageBody);
    jmsPublisher.publish(ACCConstants.ACL_SCAN_DOOR_MESSAGE_TOPIC, jmsEvent, Boolean.TRUE);

    LOGGER.info("Successfully published location and delivery information to ACL {}", messageBody);
  }
}
