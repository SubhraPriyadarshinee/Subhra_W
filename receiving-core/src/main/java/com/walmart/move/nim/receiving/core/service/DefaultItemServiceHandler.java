package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.model.ItemOverrideRequest;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component(ReceivingConstants.DEFAULT_ITEM_SERVICE_HANDLER)
public class DefaultItemServiceHandler implements ItemServiceHandler {
  @Autowired private DeliveryItemOverrideService deliveryItemOverrideService;

  @Override
  public void updateItemProperties(
      ItemOverrideRequest itemOverrideRequest, HttpHeaders httpHeaders) {
    deliveryItemOverrideService.updateDeliveryItemOverride(itemOverrideRequest, httpHeaders);
  }
}
