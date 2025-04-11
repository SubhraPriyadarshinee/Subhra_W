package com.walmart.move.nim.receiving.rdc.service;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingNotImplementedException;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.model.DeliveryDoorSummary;
import com.walmart.move.nim.receiving.core.model.audit.AuditFlagResponse;
import com.walmart.move.nim.receiving.core.model.delivery.meta.DeliveryPOMap;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.repositories.DeliveryMetaDataRepository;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

public class RdcDeliveryMetaDataService extends DeliveryMetaDataService {

  private static final Logger LOGGER = LoggerFactory.getLogger(RdcDeliveryMetaDataService.class);
  @Autowired private DeliveryMetaDataRepository deliveryMetaDataRepository;

  /**
   * This method is responsible for fetching list of {@link DeliveryMetaData} where
   * unloadingCompleteDate is greater than the allowed no of days after unloading complete and
   * osdrLastProcessedDate is either not available or not processed with in the frequency interval.
   *
   * <p>Then it will update all those {@link DeliveryMetaData} for osdr last processed date with
   * current date and time.
   *
   * @param allowedNoOfDaysAfterUnloadingComplete
   * @param frequencyIntervalInMinutes
   * @return
   */
  @Override
  @Transactional
  @InjectTenantFilter
  public List<DeliveryMetaData> findAndUpdateForOsdrProcessing(
      int allowedNoOfDaysAfterUnloadingComplete,
      long frequencyIntervalInMinutes,
      int pageSize,
      DeliveryPOMap deliveryPOMap) {
    LOGGER.info(
        "Querying delivery metadata with allowedNoOfDaysAfterUnloadingComplete:{}, frequencyIntervalInMinutes:{}, pageSize:{}",
        allowedNoOfDaysAfterUnloadingComplete,
        frequencyIntervalInMinutes,
        pageSize);
    Date unloadingCompleteDate =
        Date.from(Instant.now().minus(Duration.ofDays(allowedNoOfDaysAfterUnloadingComplete)));
    Date osdrLastProcessedDate =
        Date.from(Instant.now().minus(Duration.ofMinutes(frequencyIntervalInMinutes)));
    List<DeliveryMetaData> deliveryMetaDataList =
        deliveryMetaDataRepository
            .findByDeliveryStatusAndUnloadingCompleteDateAfterAndOsdrLastProcessedDateBeforeOrDeliveryStatusAndUnloadingCompleteDateAfterAndOsdrLastProcessedDateIsNull(
                DeliveryStatus.COMPLETE,
                unloadingCompleteDate,
                osdrLastProcessedDate,
                DeliveryStatus.COMPLETE,
                unloadingCompleteDate,
                PageRequest.of(0, pageSize));

    if (CollectionUtils.isNotEmpty(deliveryMetaDataList)) {
      for (DeliveryMetaData deliveryMetaData : deliveryMetaDataList) {
        deliveryMetaData.setOsdrLastProcessedDate(new Date());
      }
      deliveryMetaDataRepository.saveAll(deliveryMetaDataList);
    }

    return deliveryMetaDataList;
  }

  @Transactional
  @InjectTenantFilter
  public void updateDeliveryMetaData(Long deliveryNumber, String deliveryStatus) {
    List<DeliveryMetaData> deliveryMetaDataList =
        deliveryMetaDataRepository.findDeliveryMetaDataByDeliveryNumber(
            String.valueOf(deliveryNumber));
    if (CollectionUtils.isNotEmpty(deliveryMetaDataList)) {
      DeliveryMetaData deliveryMetaData = deliveryMetaDataList.get(0);
      LOGGER.info(
          "Got delivery metadata for delivery:{} with UnloadingCompleteDate:{}",
          deliveryNumber,
          deliveryMetaData.getUnloadingCompleteDate());
      if (Objects.isNull(deliveryMetaData.getUnloadingCompleteDate())) {
        deliveryMetaData.setUnloadingCompleteDate(new Date());
      }

      if (deliveryStatus.equalsIgnoreCase(DeliveryStatus.UNLOADING_COMPLETE.name())) {
        deliveryMetaData.setDeliveryStatus(DeliveryStatus.UNLOADING_COMPLETE);
      } else {
        deliveryMetaData.setOsdrLastProcessedDate(new Date());
        deliveryMetaData.setDeliveryStatus(DeliveryStatus.COMPLETE);
      }
      deliveryMetaDataRepository.save(deliveryMetaData);
      LOGGER.info("Delivery:{} successfully updated with UnloadingCompleteDate ", deliveryNumber);
    }
  }

  @Transactional
  @InjectTenantFilter
  public DeliveryMetaData findDeliveryMetaData(Long deliveryNumber) {
    DeliveryMetaData deliveryMetaData = null;
    List<DeliveryMetaData> deliveryMetaDataList =
        deliveryMetaDataRepository.findDeliveryMetaDataByDeliveryNumber(
            String.valueOf(deliveryNumber));
    if (CollectionUtils.isNotEmpty(deliveryMetaDataList)) {
      deliveryMetaData = deliveryMetaDataList.get(0);
      LOGGER.info("Delivery found for given deliveryNumber:{}", deliveryNumber);
    }
    return deliveryMetaData;
  }

  @Override
  public void updateAuditInfo(
      DeliveryMetaData deliveryMetaData, List<AuditFlagResponse> auditFlagResponseList) {
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  public void updateDeliveryMetaDataForItemOverrides(
      DeliveryMetaData deliveryMetaData,
      String itemNumber,
      String rotateDate,
      String divertDestination) {
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  public boolean updateAuditInfoInDeliveryMetaData(
      List<PurchaseOrder> purchaseOrders, int receivedQty, long deliveryNumber) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  public int getReceivedQtyFromMetadata(Long itemNumber, long deliveryNumber) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  public DeliveryDoorSummary findDoorStatus(
      Integer facilityNumber, String countryCode, String doorNumber) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  public List<DeliveryMetaData> findActiveDelivery() {
    return deliveryMetaDataRepository.findAllByDeliveryStatus(DeliveryStatus.WRK);
  }
}
