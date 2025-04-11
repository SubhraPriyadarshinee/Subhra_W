package com.walmart.move.nim.receiving.witron.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.INVALID_DATA;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.RECEIVING_INTERNAL_ERROR;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.apache.commons.lang.StringUtils.isNotBlank;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.UnloaderInfo;
import com.walmart.move.nim.receiving.core.event.processor.unload.DeliveryUnloaderProcessor;
import com.walmart.move.nim.receiving.core.model.delivery.UnloaderInfoDTO;
import com.walmart.move.nim.receiving.core.repositories.UnloaderInfoRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryUnloaderEventType;
import com.walmart.move.nim.receiving.witron.constants.GdcConstants;
import java.util.List;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

public class GdcDeliveryUnloaderProcessor implements DeliveryUnloaderProcessor {

  private static final Logger log = LoggerFactory.getLogger(GdcDeliveryUnloaderProcessor.class);
  @Autowired private DeliveryStatusPublisher deliveryStatusPublisher;
  @Autowired private TenantSpecificConfigReader configUtils;

  @Autowired private UnloaderInfoRepository unloaderInfoRepository;

  @Override
  public void publishDeliveryEvent(
      long deliveryNumber, String deliveryEventType, HttpHeaders headers)
      throws ReceivingBadDataException {
    String validUnloaderEventTypes =
        configUtils.getCcmValue(
            TenantContext.getFacilityNum(),
            VALID_UNLOADER_EVENT_TYPES,
            GdcConstants.DEFAULT_VALID_UNLOADER_EVENT_TYPES);
    if (!validUnloaderEventTypes.contains(deliveryEventType)) {
      log.error(
          "eventType={} is not valid for deliveryNumber={}", deliveryEventType, deliveryNumber);
      throw new ReceivingBadDataException(INVALID_DATA, INVALID_UNLOADER_EVENT_TYPE);
    }
    try {
      // Publish deliveryEventType to GDM
      deliveryStatusPublisher.publishDeliveryStatus(
          deliveryNumber,
          DeliveryUnloaderEventType.getDeliveryEventType(deliveryEventType).name(),
          null,
          ReceivingUtils.getForwardablHeader(headers));
      log.info(
          "Unloader deliveryEventType={} published for delivery number={}",
          deliveryEventType,
          deliveryNumber);
    } catch (Exception ex) {
      log.error(
          "Error in publishing Unloader deliveryEventType={} message for the delivery {} with exception - {}",
          deliveryEventType,
          deliveryNumber,
          ExceptionUtils.getStackTrace(ex));
      throw new ReceivingBadDataException(
          RECEIVING_INTERNAL_ERROR,
          String.format(
              DELIVERY_UNLOADER_PUBLISH_DEFAULT_ERROR_MESSAGE, deliveryNumber, ex.getMessage()));
    }
  }

  @Override
  @InjectTenantFilter
  public void saveUnloaderInfo(UnloaderInfoDTO unloaderInfo, HttpHeaders headers)
      throws ReceivingBadDataException {
    try {
      UnloaderInfo unloaderInfoToDb = new UnloaderInfo();
      unloaderInfoToDb.setDeliveryNumber(unloaderInfo.getDeliveryNumber());
      unloaderInfoToDb.setPurchaseReferenceNumber(unloaderInfo.getPurchaseReferenceNumber());
      unloaderInfoToDb.setPurchaseReferenceLineNumber(
          unloaderInfo.getPurchaseReferenceLineNumber());
      unloaderInfoToDb.setItemNumber(unloaderInfo.getItemNumber());
      unloaderInfoToDb.setActualHi(unloaderInfo.getActualHi());
      unloaderInfoToDb.setActualTi(unloaderInfo.getActualTi());
      unloaderInfoToDb.setFbq(unloaderInfo.getFbq());
      unloaderInfoToDb.setCaseQty(unloaderInfo.getCaseQty());
      unloaderInfoToDb.setPalletQty(unloaderInfo.getPalletQty());
      unloaderInfoToDb.setUnloadedFullFbq(unloaderInfo.isUnloadedFullFbq());
      unloaderInfoToDb.setCreateUserId(headers.getFirst(USER_ID_HEADER_KEY));
      unloaderInfoToDb.setOrgUnitId(
          isNotBlank(headers.getFirst(ORG_UNIT_ID_HEADER))
              ? Integer.valueOf(headers.getFirst(ORG_UNIT_ID_HEADER))
              : null);
      unloaderInfoRepository.save(unloaderInfoToDb);
      log.info("persisted unloaderInfo={}", unloaderInfoToDb);
    } catch (Exception ex) {
      log.error(
          "Error in persisting unloaderInfo={} with exception - {}",
          unloaderInfo,
          ExceptionUtils.getStackTrace(ex));
      throw new ReceivingBadDataException(
          RECEIVING_INTERNAL_ERROR,
          String.format(
              DELIVERY_UNLOADER_PERSIST_DEFAULT_ERROR_MESSAGE,
              unloaderInfo.getDeliveryNumber(),
              ex.getMessage()));
    }
  }

  @Override
  @InjectTenantFilter
  public List<UnloaderInfo> getUnloaderInfo(
      Long deliveryNumber, String poNumber, Integer poLineNumber) throws ReceivingBadDataException {
    List<UnloaderInfo> unloaderInfoList = null;
    try {
      if (isNotBlank(poNumber)) {
        unloaderInfoList =
            unloaderInfoRepository
                .findByDeliveryNumberAndFacilityCountryCodeAndFacilityNumAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                    deliveryNumber,
                    TenantContext.getFacilityCountryCode(),
                    TenantContext.getFacilityNum(),
                    poNumber,
                    poLineNumber);
      } else {
        unloaderInfoList =
            unloaderInfoRepository.findByDeliveryNumberAndFacilityCountryCodeAndFacilityNum(
                deliveryNumber,
                TenantContext.getFacilityCountryCode(),
                TenantContext.getFacilityNum());
      }
    } catch (Exception ex) {
      log.error(
          "Error during getunloaderInfo for deliveryNumber={}, poNumber={},poLineNumber={} with exception - {}",
          deliveryNumber,
          poNumber,
          poLineNumber,
          ExceptionUtils.getStackTrace(ex));
      throw new ReceivingBadDataException(
          RECEIVING_INTERNAL_ERROR,
          String.format(
              DELIVERY_UNLOADER_GET_DEFAULT_ERROR_MESSAGE, deliveryNumber, ex.getMessage()));
    }
    return unloaderInfoList;
  }
}
