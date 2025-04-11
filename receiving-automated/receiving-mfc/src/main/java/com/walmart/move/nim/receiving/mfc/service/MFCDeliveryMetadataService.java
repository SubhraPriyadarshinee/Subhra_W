package com.walmart.move.nim.receiving.mfc.service;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingNotImplementedException;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.model.DeliveryDoorSummary;
import com.walmart.move.nim.receiving.core.model.audit.AuditFlagResponse;
import com.walmart.move.nim.receiving.core.model.delivery.meta.DeliveryPOMap;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.mfc.common.StoreDeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.util.List;
import java.util.Optional;
import javax.persistence.PersistenceException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;

public class MFCDeliveryMetadataService extends DeliveryMetaDataService {

  public List<DeliveryMetaData> findAllByDeliveryNumberIn(List<String> deliveryNumberList) {
    return deliveryMetaDataRepository.findAllByDeliveryNumberInOrderByCreatedDate(
        deliveryNumberList);
  }

  /**
   * Usecase - In auto MFC user is decanting the item in ms and atlas is processing delivery
   * metadata update for all the decanted message causing race condition due to optimistic locking.
   * Retryable will retry the operation and update/insert based on need.
   */
  @Retryable(
      value = {PersistenceException.class},
      maxAttemptsExpression = "${max.retry.count}",
      backoff = @Backoff(delayExpression = "${retry.delay}"))
  @Override
  public DeliveryMetaData save(DeliveryMetaData deliveryMetaData) {
    Optional<DeliveryMetaData> deliveryMetaDataOptional =
        findByDeliveryNumber(deliveryMetaData.getDeliveryNumber());
    if (deliveryMetaDataOptional.isPresent()) {
      DeliveryMetaData existingRecord = deliveryMetaDataOptional.get();
      deliveryMetaData.setVersion(existingRecord.getVersion());
    }
    return super.save(deliveryMetaData);
  }

  @Override
  public void updateAuditInfo(
      DeliveryMetaData deliveryMetaData, List<AuditFlagResponse> auditFlagResponseList) {
    throw new ReceivingDataNotFoundException("NOT_SUPPORTED", " No implementation found");
  }

  @Override
  public void updateDeliveryMetaDataForItemOverrides(
      DeliveryMetaData deliveryMetaData,
      String itemNumber,
      String rotateDate,
      String divertDestination) {
    throw new ReceivingDataNotFoundException("NOT_SUPPORTED", " No implementation found");
  }

  @Override
  public List<DeliveryMetaData> findAndUpdateForOsdrProcessing(
      int allowedNoOfDaysAfterUnloadingComplete,
      long frequencyIntervalInMinutes,
      int pageSize,
      DeliveryPOMap deliveryPOMap) {
    throw new ReceivingDataNotFoundException("NOT_SUPPORTED", " No implementation found");
  }

  @Transactional
  @InjectTenantFilter
  public List<DeliveryMetaData> findByDeliveryStatus(
      DeliveryStatus deliveryStatus, Pageable pageable) {
    return deliveryMetaDataRepository.findByDeliveryStatus(deliveryStatus, pageable);
  }

  @Transactional
  @InjectTenantFilter
  public Page<DeliveryMetaData> findByDeliveryStatusIn(
      List<DeliveryStatus> deliveryStatuses, Pageable pageable) {
    return deliveryMetaDataRepository.findByDeliveryStatusIn(deliveryStatuses, pageable);
  }

  @Override
  @Transactional
  @InjectTenantFilter
  public void findAndUpdateDeliveryStatus(String deliveryNumber, DeliveryStatus deliveryStatus)
      throws ReceivingException {
    DeliveryMetaData deliveryMetaData =
        findByDeliveryNumber(deliveryNumber)
            .orElseThrow(
                () ->
                    new ReceivingInternalException(
                        ExceptionCodes.DELIVERY_METADATA_NOT_FOUND,
                        String.format("Delivery Metadata not found delivery=%s", deliveryNumber)));
    if (!StoreDeliveryStatus.isValidDeliveryStatusForUpdate(
        StoreDeliveryStatus.getDeliveryStatus(deliveryMetaData.getDeliveryStatus()),
        StoreDeliveryStatus.getDeliveryStatus(deliveryStatus))) {
      throw new ReceivingInternalException(
          ExceptionCodes.INVALID_DELIVERY_STATUS_UPDATE_REQUEST,
          String.format(
              "Delivery=%s is already in %s state.", deliveryNumber, deliveryStatus.name()));
    }
    deliveryMetaData.setDeliveryStatus(deliveryStatus);
    deliveryMetaDataRepository.save(deliveryMetaData);
  }

  @Override
  public boolean updateAuditInfoInDeliveryMetaData(
      List<PurchaseOrder> purchaseOrders, int receivedQty, long deliveryNumber) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED, " No implementation found");
  }

  @Override
  public int getReceivedQtyFromMetadata(Long itemNumber, long deliveryNumber) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED, " No implementation found");
  }

  @Override
  public DeliveryDoorSummary findDoorStatus(
      Integer facilityNumber, String countryCode, String doorNumber) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED, " No implementation found");
  }
}
