package com.walmart.move.nim.receiving.core.message.publisher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Date;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component(ReceivingConstants.JMS_RECEIPT_PUBLISHER)
public class JMSReceiptPublisher implements MessagePublisher<ContainerDTO> {

  @Value("${pub.receipts.topic}")
  protected String pubReceiptsTopic;

  private static final Logger LOGGER = LoggerFactory.getLogger(JMSReceiptPublisher.class);
  private final Gson gson;
  @Autowired private JmsPublisher jmsPublisher;

  public JMSReceiptPublisher() {
    gson =
        new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter())
            .create();
  }

  @Override
  public void publish(ContainerDTO container, Map<String, Object> headers) {
    // converting container Object into String
    String jsonObject = gson.toJson(container);
    // publishing container information to inventory
    ReceivingJMSEvent jmsEvent = new ReceivingJMSEvent(headers, jsonObject);
    jmsPublisher.publish(pubReceiptsTopic, jmsEvent, Boolean.TRUE);
  }
}
