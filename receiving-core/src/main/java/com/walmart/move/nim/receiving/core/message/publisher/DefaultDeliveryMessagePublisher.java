package com.walmart.move.nim.receiving.core.message.publisher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Date;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component(ReceivingConstants.DEFAULT_DELIVERY_STATUS_PUBLISHER)
public class DefaultDeliveryMessagePublisher implements MessagePublisher<DeliveryInfo> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DefaultDeliveryMessagePublisher.class);

  @Autowired private JmsPublisher jmsPublisher;

  @Value("${pub.delivery.status.topic}")
  protected String pubDeliveryStatusTopic;

  private Gson gson;

  public DefaultDeliveryMessagePublisher() {
    gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  @Override
  public void publish(DeliveryInfo deliveryInfo, Map<String, Object> headers) {
    String payload = gson.toJson(deliveryInfo);
    ReceivingJMSEvent receivingJMSEvent = new ReceivingJMSEvent(headers, payload);
    LOGGER.debug(
        "{} {} {} {}",
        "Delivery status changed to ",
        deliveryInfo.getDeliveryStatus(),
        "jms publish event ",
        payload);
    jmsPublisher.publish(pubDeliveryStatusTopic, receivingJMSEvent, Boolean.TRUE);
  }
}
