package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import org.springframework.http.HttpHeaders;

public interface CompleteDeliveryProcessor {

  DeliveryInfo completeDelivery(Long deliveryNumber, boolean performUnload, HttpHeaders headers)
      throws ReceivingException;

  void autoCompleteDeliveries(Integer facilityNumber) throws ReceivingException;

  DeliveryInfo completeDeliveryAndPO(Long deliveryNumber, HttpHeaders headers)
      throws ReceivingException;
}
