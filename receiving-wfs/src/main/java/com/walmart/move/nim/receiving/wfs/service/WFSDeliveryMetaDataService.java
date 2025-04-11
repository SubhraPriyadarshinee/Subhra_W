package com.walmart.move.nim.receiving.wfs.service;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingNotImplementedException;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.model.DeliveryDoorSummary;
import com.walmart.move.nim.receiving.core.model.audit.AuditFlagResponse;
import com.walmart.move.nim.receiving.core.model.delivery.meta.DeliveryPOMap;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WFSDeliveryMetaDataService extends DeliveryMetaDataService {

  private static final Logger LOGGER = LoggerFactory.getLogger(WFSDeliveryMetaDataService.class);

  @Override
  public void updateAuditInfo(
      DeliveryMetaData deliveryMetaData, List<AuditFlagResponse> auditFlagResponseList) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED,
        ReceivingConstants.FEATURE_NOT_IMPLEMENTED_ERROR_MESSAGE);
  }

  @Override
  public void updateDeliveryMetaDataForItemOverrides(
      DeliveryMetaData deliveryMetaData,
      String itemNumber,
      String rotateDate,
      String divertDestination) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED,
        ReceivingConstants.FEATURE_NOT_IMPLEMENTED_ERROR_MESSAGE);
  }

  @Override
  public List<DeliveryMetaData> findAndUpdateForOsdrProcessing(
      int allowedNoOfDaysAfterUnloadingComplete,
      long frequencyIntervalInMinutes,
      int pageSize,
      DeliveryPOMap deliveryPOMap) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED,
        ReceivingConstants.FEATURE_NOT_IMPLEMENTED_ERROR_MESSAGE);
  }

  @Override
  public void findAndUpdateDeliveryStatus(String deliveryNumber, DeliveryStatus deliveryStatus) {
    LOGGER.info("method findAndUpdateDeliveryStatus not implemented for WFS Facility");
  }

  @Override
  public boolean updateAuditInfoInDeliveryMetaData(
      List<PurchaseOrder> purchaseOrders, int receivedQty, long deliveryNumber) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED,
        ReceivingConstants.FEATURE_NOT_IMPLEMENTED_ERROR_MESSAGE);
  }

  @Override
  public int getReceivedQtyFromMetadata(Long itemNumber, long deliveryNumber) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED,
        ReceivingConstants.FEATURE_NOT_IMPLEMENTED_ERROR_MESSAGE);
  }

  @Override
  public DeliveryDoorSummary findDoorStatus(
      Integer facilityNumber, String countryCode, String doorNumber) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED,
        ReceivingConstants.FEATURE_NOT_IMPLEMENTED_ERROR_MESSAGE);
  }
}
