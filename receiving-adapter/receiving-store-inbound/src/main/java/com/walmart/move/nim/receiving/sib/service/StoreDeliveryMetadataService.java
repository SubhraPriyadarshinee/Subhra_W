package com.walmart.move.nim.receiving.sib.service;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingNotImplementedException;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.model.DeliveryDoorSummary;
import com.walmart.move.nim.receiving.core.model.audit.AuditFlagResponse;
import com.walmart.move.nim.receiving.core.model.delivery.meta.DeliveryPOMap;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import java.util.List;
import java.util.Optional;

public class StoreDeliveryMetadataService extends DeliveryMetaDataService {

  public Optional<DeliveryMetaData> findDeliveryMetadataByDeliveryNumber(String deliveryMetadata) {
    return deliveryMetaDataRepository.findByDeliveryNumber(deliveryMetadata);
  }

  @Override
  public void updateAuditInfo(
      DeliveryMetaData deliveryMetaData, List<AuditFlagResponse> auditFlagResponseList) {
    throw new RuntimeException("Functionality not supported");
  }

  @Override
  public void updateDeliveryMetaDataForItemOverrides(
      DeliveryMetaData deliveryMetaData,
      String itemNumber,
      String rotateDate,
      String divertDestination) {
    throw new RuntimeException("Functionality not supported");
  }

  @Override
  public List<DeliveryMetaData> findAndUpdateForOsdrProcessing(
      int allowedNoOfDaysAfterUnloadingComplete,
      long frequencyIntervalInMinutes,
      int pageSize,
      DeliveryPOMap deliveryPOMap) {
    throw new RuntimeException("Functionality not supported");
  }

  @Override
  public boolean updateAuditInfoInDeliveryMetaData(
      List<PurchaseOrder> purchaseOrders, int receivedQty, long deliveryNumber) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED, "Functionality not supported");
  }

  @Override
  public int getReceivedQtyFromMetadata(Long iteNumber, long deliveryNumber) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED, "Functionality not supported");
  }

  @Override
  public DeliveryDoorSummary findDoorStatus(
      Integer facilityNumber, String countryCode, String doorNumber) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED, "Functionality not supported");
  }
}
