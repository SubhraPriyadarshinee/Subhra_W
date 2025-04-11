package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingNotImplementedException;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.model.DeliveryDoorSummary;
import com.walmart.move.nim.receiving.core.model.audit.AuditFlagResponse;
import com.walmart.move.nim.receiving.core.model.delivery.meta.DeliveryPOMap;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.repositories.DeliveryMetaDataRepository;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

// TODO ? Need to evaluate why it is an abstract class
public abstract class DeliveryMetaDataService implements Purge {
  private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryMetaDataService.class);

  @Autowired protected DeliveryMetaDataRepository deliveryMetaDataRepository;

  @Transactional
  @InjectTenantFilter
  public DeliveryMetaData save(DeliveryMetaData deliveryMetaData) {
    return deliveryMetaDataRepository.save(deliveryMetaData);
  }

  public Iterable<DeliveryMetaData> saveAll(List<DeliveryMetaData> deliveryMetaDataList) {
    if (CollectionUtils.isEmpty(deliveryMetaDataList)) {
      return null;
    }
    return deliveryMetaDataRepository.saveAll(deliveryMetaDataList);
  }

  public abstract void updateAuditInfo(
      DeliveryMetaData deliveryMetaData, List<AuditFlagResponse> auditFlagResponseList);

  public abstract void updateDeliveryMetaDataForItemOverrides(
      DeliveryMetaData deliveryMetaData,
      String itemNumber,
      String rotateDate,
      String divertDestination);

  public abstract List<DeliveryMetaData> findAndUpdateForOsdrProcessing(
      int allowedNoOfDaysAfterUnloadingComplete,
      long frequencyIntervalInMinutes,
      int pageSize,
      DeliveryPOMap deliveryPOMap);

  @Transactional
  @InjectTenantFilter
  public Optional<DeliveryMetaData> findByDeliveryNumber(String deliveryNumber) {
    return deliveryMetaDataRepository.findByDeliveryNumber(deliveryNumber);
  }

  @Transactional
  @InjectTenantFilter
  public void deleteByDeliveryNumber(String deliveryNumber) {
    deliveryMetaDataRepository.deleteByDeliveryNumber(deliveryNumber);
  }

  public void findAndUpdateDeliveryStatus(String deliveryNumber, DeliveryStatus deliveryStatus)
      throws ReceivingException {
    throw new ReceivingException(
        ReceivingConstants.METHOD_NOT_ALLOWED,
        HttpStatus.METHOD_NOT_ALLOWED,
        ExceptionCodes.METHOD_NOT_ALLOWED);
  }

  public void completeSystematicallyReopenedDeliveriesBefore(Date beforeDate)
      throws ReceivingException {
    throw new ReceivingException(
        ReceivingConstants.METHOD_NOT_ALLOWED,
        HttpStatus.METHOD_NOT_ALLOWED,
        ExceptionCodes.METHOD_NOT_ALLOWED);
  }

  @Override
  @Transactional
  public long purge(PurgeData purgeEntity, PageRequest pageRequest, int purgeEntitiesBeforeXdays) {
    List<DeliveryMetaData> deliveryMetaDataList =
        deliveryMetaDataRepository.findByIdGreaterThanEqual(
            purgeEntity.getLastDeleteId(), pageRequest);

    Date deleteDate = getPurgeDate(purgeEntitiesBeforeXdays);

    // filter out list by validating last createTs
    deliveryMetaDataList =
        deliveryMetaDataList
            .stream()
            .filter(metaData -> metaData.getCreatedDate().before(deleteDate))
            .sorted(Comparator.comparing(DeliveryMetaData::getId))
            .collect(Collectors.toList());

    if (CollectionUtils.isEmpty(deliveryMetaDataList)) {
      LOGGER.info("Purge DELIVERY_METADATA: Nothing to delete");
      return purgeEntity.getLastDeleteId();
    }
    long lastDeletedId = deliveryMetaDataList.get(deliveryMetaDataList.size() - 1).getId();

    LOGGER.info(
        "Purge DELIVERY_METADATA: {} records : ID {} to {} : START",
        deliveryMetaDataList.size(),
        deliveryMetaDataList.get(0).getId(),
        lastDeletedId);
    deliveryMetaDataRepository.deleteAll(deliveryMetaDataList);
    LOGGER.info("Purge DELIVERY_METADATA: END");
    return lastDeletedId;
  }

  @Transactional
  @InjectTenantFilter
  public List<DeliveryMetaData> findAllByDeliveryNumber(List<String> deliveryNos) {
    return deliveryMetaDataRepository.findAllByDeliveryNumberInOrderByCreatedDate(deliveryNos);
  }

  public List<DeliveryMetaData> findActiveDelivery() {
    return deliveryMetaDataRepository.findAllByDeliveryStatus(DeliveryStatus.WRK);
  }

  public abstract boolean updateAuditInfoInDeliveryMetaData(
      List<PurchaseOrder> purchaseOrders, int receivedQty, long deliveryNumber);

  public abstract int getReceivedQtyFromMetadata(Long itemNumber, long deliveryNumber);

  public int getReceivedQtyFromMetadataWithoutAuditCheck(Long itemNumber, long deliveryNumber){
    throw new ReceivingNotImplementedException(
            ExceptionCodes.FEATURE_NOT_IMPLEMENTED,
            ReceivingConstants.FEATURE_NOT_IMPLEMENTED_ERROR_MESSAGE);
  }

  public abstract DeliveryDoorSummary findDoorStatus(
      Integer facilityNumber, String countryCode, String doorNumber) throws ReceivingException;
}
