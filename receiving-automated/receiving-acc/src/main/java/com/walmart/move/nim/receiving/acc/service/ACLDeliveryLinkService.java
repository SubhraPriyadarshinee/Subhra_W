package com.walmart.move.nim.receiving.acc.service;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.message.publisher.JMSDeliveryLinkPublisher;
import com.walmart.move.nim.receiving.acc.model.acl.notification.DeliveryAndLocationMessage;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

public class ACLDeliveryLinkService implements DeliveryLinkService {

  @Autowired private JMSDeliveryLinkPublisher jmsDeliveryLinkPublisher;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Autowired private Gson gson;

  @Override
  public void updateDeliveryLink(
      List<DeliveryAndLocationMessage> deliveryAndLocationMessage, HttpHeaders headers) {
    Map<String, Object> messageHeaders = new HashMap<>();
    headers.forEach(messageHeaders::put);

    jmsDeliveryLinkPublisher.publish(
        tenantSpecificConfigReader.isFeatureFlagEnabled(ACCConstants.ENABLE_MULTI_DELIVERY_LINK)
            ? gson.toJson(deliveryAndLocationMessage)
            : gson.toJson(deliveryAndLocationMessage.get(0)),
        messageHeaders);
  }
}
