package com.walmart.move.nim.receiving.acc.service;

import com.walmart.move.nim.receiving.acc.model.acl.notification.DeliveryAndLocationMessage;
import java.util.List;
import org.springframework.http.HttpHeaders;

public interface DeliveryLinkService {
  void updateDeliveryLink(
      List<DeliveryAndLocationMessage> deliveryAndLocationMessage, HttpHeaders headers);
}
