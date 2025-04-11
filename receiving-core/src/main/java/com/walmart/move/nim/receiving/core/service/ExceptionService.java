package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;

import com.walmart.move.nim.receiving.core.client.hawkeye.model.DeliverySearchRequest;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
public class ExceptionService {

  @Autowired TenantSpecificConfigReader tenantSpecificConfigReader;

  public InstructionResponse receiveException(
      ReceiveExceptionRequest receiveExceptionRequest, HttpHeaders httpHeaders)
      throws ReceivingException {
    ReceiveExceptionHandler receiveExceptionHandler =
        tenantSpecificConfigReader.getConfiguredInstance(
            getFacilityNum().toString(),
            ReceivingConstants.RECEIVE_EXCEPTION_HANDLER_KEY,
            ReceiveExceptionHandler.class);
    return receiveExceptionHandler.receiveException(receiveExceptionRequest, httpHeaders);
  }

  public List<DeliveryDocument> getDeliveryDocumentsForDeliverySearch(
      DeliverySearchRequest deliverySearchRequest, HttpHeaders httpHeaders)
      throws ReceivingBadDataException {
    ReceiveExceptionHandler receiveExceptionHandler =
        tenantSpecificConfigReader.getConfiguredInstance(
            getFacilityNum().toString(),
            ReceivingConstants.RECEIVE_EXCEPTION_HANDLER_KEY,
            ReceiveExceptionHandler.class);
    return receiveExceptionHandler.getDeliveryDocumentsForDeliverySearch(
        deliverySearchRequest, httpHeaders);
  }

  public Map<String, Object> printShippingLabel(String trackingId, HttpHeaders httpHeaders)
      throws ReceivingException {
    ReceiveExceptionHandler receiveExceptionHandler =
        tenantSpecificConfigReader.getConfiguredInstance(
            getFacilityNum().toString(),
            ReceivingConstants.RECEIVE_EXCEPTION_HANDLER_KEY,
            ReceiveExceptionHandler.class);
    return receiveExceptionHandler.printShippingLabel(trackingId, httpHeaders);
  }

  public void inventoryContainerUpdate(
      InventoryUpdateRequest inventoryUpdateRequest, HttpHeaders httpHeaders) {
    ReceiveExceptionHandler receiveExceptionHandler =
        tenantSpecificConfigReader.getConfiguredInstance(
            getFacilityNum().toString(),
            ReceivingConstants.RECEIVE_EXCEPTION_HANDLER_KEY,
            ReceiveExceptionHandler.class);
    receiveExceptionHandler.inventoryContainerUpdate(inventoryUpdateRequest, httpHeaders);
  }
}
