package com.walmart.move.nim.receiving.core.event.processor.unload;

import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component(ReceivingConstants.DEFAULT_DELIVERY_UNLOADING_PROCESSOR)
public class DefaultDeliveryUnloadingProcessor implements DeliveryUnloadingProcessor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DefaultDeliveryUnloadingProcessor.class);

  @Override
  public void doProcess(DeliveryInfo deliveryInfo) {
    LOGGER.warn(
        "No Market specific DeliveryUnloadingProcessor Configured for delivery={} and facility={}",
        deliveryInfo.getDeliveryNumber(),
        TenantContext.getFacilityNum());
  }
}
