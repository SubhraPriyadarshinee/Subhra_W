package com.walmart.move.nim.receiving.core.message.publisher;

import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.model.PublishInstructionSummary;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component(ReceivingConstants.JMS_INSTRUCTION_PUBLISHER)
public class JMSInstructionPublisher extends InstructionPublisher
    implements MessagePublisher<PublishInstructionSummary> {

  @Autowired private JmsPublisher jmsPublisher;

  @Override
  public void publish(
      PublishInstructionSummary publishInstructionSummary, Map<String, Object> messageHeader) {
    String payload = gson.toJson(publishInstructionSummary);
    ReceivingJMSEvent receivingJMSEvent = new ReceivingJMSEvent(messageHeader, payload);

    jmsPublisher.publish(
        ReceivingConstants.PUB_INSTRUCTION_TOPIC, receivingJMSEvent, Boolean.FALSE);
  }
}
