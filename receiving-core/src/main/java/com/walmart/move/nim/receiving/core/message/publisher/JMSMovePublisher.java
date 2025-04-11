package com.walmart.move.nim.receiving.core.message.publisher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.model.move.MoveData;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Date;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component(ReceivingConstants.JMS_MOVE_PUBLISHER)
public class JMSMovePublisher implements MessagePublisher<MoveData> {

  @Value("${pub.move.topic}")
  protected String pubMoveTopic;

  private static final Logger LOGGER = LoggerFactory.getLogger(JMSMovePublisher.class);
  private Gson gson;
  @Autowired private JmsPublisher jmsPublisher;

  public JMSMovePublisher() {
    gson =
        new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter())
            .create();
  }

  @Override
  public void publish(MoveData moveInfo, Map<String, Object> headers) {
    ReceivingJMSEvent receivingJMSEvent = new ReceivingJMSEvent(headers, gson.toJson(moveInfo));
    LOGGER.info("Publishing move {}", moveInfo);
    jmsPublisher.publish(pubMoveTopic, receivingJMSEvent, Boolean.TRUE);
  }
}
