package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.client.hawkeye.model.DeliverySearchRequest;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.InventoryUpdateRequest;
import com.walmart.move.nim.receiving.core.model.ReceiveExceptionRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

public class DefaultReceiveExceptionHandler implements ReceiveExceptionHandler {
  private static final Logger logger =
      LoggerFactory.getLogger(DefaultReceiveInstructionHandler.class);

  @Override
  public InstructionResponse receiveException(
      ReceiveExceptionRequest receiveExceptionRequest, HttpHeaders httpHeaders) {
    logger.info(
        "Default implementation of receive exception for exception request {}",
        receiveExceptionRequest);
    return null;
  }

  @Override
  public List<DeliveryDocument> getDeliveryDocumentsForDeliverySearch(
      DeliverySearchRequest deliverySearchRequest, HttpHeaders httpHeaders) {
    logger.info(
        "Default implementation of getDeliveryDocumentsFromDeliverySearch for Request {}",
        deliverySearchRequest);
    return Collections.emptyList();
  }

  @Override
  public Map<String, Object> printShippingLabel(String trackingId, HttpHeaders httpHeaders) {
    logger.info("Default implementation of printShippingLabel for Request {}", trackingId);
    return Collections.emptyMap();
  }

  @Override
  public void inventoryContainerUpdate(
      InventoryUpdateRequest inventoryUpdateRequest, HttpHeaders httpHeaders) {
    logger.info(
        "Default implementation of inventoryContainerUpdate for Request {}",
        inventoryUpdateRequest);
  }
}
