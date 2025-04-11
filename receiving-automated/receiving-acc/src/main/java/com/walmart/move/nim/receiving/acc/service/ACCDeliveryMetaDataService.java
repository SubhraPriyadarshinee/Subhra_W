package com.walmart.move.nim.receiving.acc.service;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingNotImplementedException;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.DeliveryDoorSummary;
import com.walmart.move.nim.receiving.core.model.audit.AuditFlagResponse;
import com.walmart.move.nim.receiving.core.model.delivery.meta.DeliveryPOMap;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.repositories.DeliveryMetaDataRepository;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

public class ACCDeliveryMetaDataService extends DeliveryMetaDataService {
  private static final Logger LOGGER = LoggerFactory.getLogger(ACCDeliveryMetaDataService.class);
  @Autowired private DeliveryMetaDataRepository deliveryMetaDataRepository;

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  private DeliveryService deliveryService;

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
  public void updateAuditInfo(
      DeliveryMetaData deliveryMetaData, List<AuditFlagResponse> auditFlagResponseList) {
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
  @Transactional
  @InjectTenantFilter
  public void findAndUpdateDeliveryStatus(String deliveryNumber, DeliveryStatus deliveryStatus) {
    DeliveryMetaData deliveryMetaData =
        deliveryMetaDataRepository.findByDeliveryNumber(deliveryNumber).orElse(null);
    if (Objects.nonNull(deliveryMetaData)) {
      deliveryMetaData.setDeliveryStatus(deliveryStatus);
      deliveryMetaData.setCreatedDate(new Date());
    } else {
      deliveryMetaData =
          DeliveryMetaData.builder()
              .deliveryNumber(deliveryNumber)
              .deliveryStatus(deliveryStatus)
              .build();
    }
    deliveryMetaDataRepository.save(deliveryMetaData);
  }

  @Override
  public void completeSystematicallyReopenedDeliveriesBefore(Date beforeDate) {
    List<DeliveryMetaData> deliveryMetaDataList =
        deliveryMetaDataRepository.findAllByDeliveryStatusAndCreatedDateLessThan(
            DeliveryStatus.SYS_REO, beforeDate);
    if (!CollectionUtils.isEmpty(deliveryMetaDataList)) {
      String correlationId = UUID.randomUUID().toString();
      deliveryMetaDataList.forEach(
          dmd -> {
            try {
              LOGGER.info(
                  "Calling systematically complete delivery for {}", dmd.getDeliveryNumber());
              ReceivingUtils.setTenantContext(
                  dmd.getFacilityNum().toString(),
                  dmd.getFacilityCountryCode(),
                  correlationId,
                  this.getClass().getName());

              deliveryService.completeDelivery(
                  Long.parseLong(dmd.getDeliveryNumber()), false, ReceivingUtils.getHeaders());
              // mark it as complete
              dmd.setDeliveryStatus(DeliveryStatus.COMPLETE);
              TenantContext.clear();
            } catch (Exception e) {
              LOGGER.error(
                  "Error while systematically completing delivery {}, exception {}",
                  dmd.getDeliveryNumber(),
                  e);
            }
          });
      deliveryMetaDataRepository.saveAll(deliveryMetaDataList);
    }
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

  @InjectTenantFilter
  public void persistMetaData(DeliveryUpdateMessage deliveryUpdateMessage) {
    Optional<DeliveryMetaData> deliveryMetaData =
        deliveryMetaDataRepository.findByDeliveryNumber(
            String.valueOf(deliveryUpdateMessage.getDeliveryNumber()));
    if (deliveryMetaData.isPresent()) {
      DeliveryMetaData deliveryMetaDataInfo = deliveryMetaData.get();
      if (Objects.nonNull(deliveryUpdateMessage.getDeliveryStatus())
          && !StringUtils.equalsIgnoreCase(
              deliveryUpdateMessage.getDeliveryStatus(),
              deliveryMetaDataInfo.getDeliveryStatus().toString())) { // Update Delivery Metadata
        deliveryMetaDataInfo.setDeliveryStatus(
            getDeliveryStatus(
                DeliveryStatus.valueOf(deliveryUpdateMessage.getDeliveryStatus()),
                deliveryUpdateMessage.getEventType()));
        deliveryMetaDataRepository.save(deliveryMetaDataInfo);
        LOGGER.info(
            "Updated the delivery status in deliveryMetadata for delivery = {} to status = {} ",
            deliveryMetaDataInfo.getDeliveryNumber(),
            deliveryUpdateMessage.getDeliveryStatus());
      }
    } else {
      DeliveryMetaData deliveryMetaDataToSave =
          DeliveryMetaData.builder()
              .deliveryNumber(String.valueOf(deliveryUpdateMessage.getDeliveryNumber()))
              .deliveryStatus(
                  getDeliveryStatus(
                      DeliveryStatus.valueOf(deliveryUpdateMessage.getDeliveryStatus()),
                      deliveryUpdateMessage.getEventType()))
              .build();
      LOGGER.info(
          "Persisting delivery: {} information into DELIVERY_METADATA table",
          deliveryUpdateMessage.getDeliveryNumber());
      deliveryMetaDataRepository.save(deliveryMetaDataToSave);
    }
  }

  private DeliveryStatus getDeliveryStatus(DeliveryStatus deliveryStatus, String eventType) {
    if (ReceivingConstants.EVENT_TYPE_FINALIZED.equals(eventType)) {
      return DeliveryStatus.COMPLETE;
    } else {
      return deliveryStatus;
    }
  }
}
