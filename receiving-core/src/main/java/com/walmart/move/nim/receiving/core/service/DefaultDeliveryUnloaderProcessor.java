package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DEFAULT_DELIVERY_UNLOADER_PROCESSOR;

import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.UnloaderInfo;
import com.walmart.move.nim.receiving.core.event.processor.unload.DeliveryUnloaderProcessor;
import com.walmart.move.nim.receiving.core.model.delivery.UnloaderInfoDTO;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component(DEFAULT_DELIVERY_UNLOADER_PROCESSOR)
public class DefaultDeliveryUnloaderProcessor implements DeliveryUnloaderProcessor {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(DefaultDeliveryUnloaderProcessor.class);

  @Override
  public void publishDeliveryEvent(
      long deliveryNumber, String deliveryEventType, HttpHeaders headers)
      throws ReceivingBadDataException {
    LOGGER.info(
        "No DeliveryUnloaderProcessor for facility number: {}", TenantContext.getFacilityNum());
  }

  @Override
  public void saveUnloaderInfo(UnloaderInfoDTO unloaderInfo, HttpHeaders headers)
      throws ReceivingBadDataException {
    LOGGER.info(
        "No DeliveryUnloaderProcessor for facility number: {}", TenantContext.getFacilityNum());
  }

  @Override
  public List<UnloaderInfo> getUnloaderInfo(
      Long deliveryNumber, String poNumber, Integer poLineNumber) throws ReceivingBadDataException {
    LOGGER.info(
        "No DeliveryUnloaderProcessor for facility number: {}", TenantContext.getFacilityNum());
    return null;
  }
}
