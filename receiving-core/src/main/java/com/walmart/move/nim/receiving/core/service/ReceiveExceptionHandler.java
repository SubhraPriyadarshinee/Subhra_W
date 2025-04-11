package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.client.hawkeye.model.DeliverySearchRequest;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.InventoryUpdateRequest;
import com.walmart.move.nim.receiving.core.model.ReceiveExceptionRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;

public interface ReceiveExceptionHandler {
  InstructionResponse receiveException(
      ReceiveExceptionRequest receiveExceptionRequest, HttpHeaders httpHeaders)
      throws ReceivingException;

  List<DeliveryDocument> getDeliveryDocumentsForDeliverySearch(
      DeliverySearchRequest deliverySearchRequest, HttpHeaders httpHeaders);

  Map<String, Object> printShippingLabel(String trackingId, HttpHeaders httpHeaders)
      throws ReceivingException;

  void inventoryContainerUpdate(
      InventoryUpdateRequest inventoryUpdateRequest, HttpHeaders httpHeaders);
}
