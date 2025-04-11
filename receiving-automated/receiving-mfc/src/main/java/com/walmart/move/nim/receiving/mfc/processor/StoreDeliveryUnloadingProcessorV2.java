package com.walmart.move.nim.receiving.mfc.processor;

import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.*;

import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.event.processor.unload.DeliveryUnloadingProcessor;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class StoreDeliveryUnloadingProcessorV2 implements DeliveryUnloadingProcessor {

  private static Logger LOGGER = LoggerFactory.getLogger(StoreDeliveryUnloadingProcessorV2.class);

  @Autowired private ProcessInitiator processInitiator;

  @Override
  public void doProcess(DeliveryInfo deliveryInfo) {
    Map<String, Object> forwardableHeaders =
        ReceivingUtils.getForwardablHeaderWithTenantData(ReceivingUtils.getHeaders());
    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .key(
                formatEventKey(
                    TenantContext.getFacilityNum(),
                    TenantContext.getFacilityCountryCode(),
                    deliveryInfo.getDeliveryNumber()))
            .payload(JacksonParser.writeValueAsString(deliveryInfo))
            .name(STORE_DELIVERY_UNLOADING_PROCESSOR)
            .additionalAttributes(forwardableHeaders)
            .processor(STORE_DELIVERY_UNLOADING_PROCESSOR)
            .build();
    LOGGER.info(
        "Initiation process for unloading for deliveryNumber - {}",
        deliveryInfo.getDeliveryNumber());
    processInitiator.initiateProcess(receivingEvent, forwardableHeaders);
    LOGGER.info(
        "Completed process for unloading for deliveryNumber - {}",
        deliveryInfo.getDeliveryNumber());
  }

  private String formatEventKey(
      Integer facilityNum, String facilityCountryCode, Long deliveryNumber) {
    return StringUtils.joinWith(UNDERSCORE, facilityCountryCode, facilityNum, deliveryNumber);
  }
}
