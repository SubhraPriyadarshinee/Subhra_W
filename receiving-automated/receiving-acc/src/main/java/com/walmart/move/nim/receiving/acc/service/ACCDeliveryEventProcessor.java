package com.walmart.move.nim.receiving.acc.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.service.DeliveryEventService;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ACCDeliveryEventProcessor implements EventProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ACCDeliveryEventProcessor.class);

  @Autowired protected TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private PreLabelDeliveryService preLabelDeliveryService;
  @Autowired private ACCDeliveryMetaDataService accDeliveryMetaDataService;

  @Resource(name = ReceivingConstants.DELIVERY_EVENT_SERVICE)
  private DeliveryEventService deliveryEventService;

  @Override
  public void processEvent(MessageData messageData) throws ReceivingException {

    DeliveryUpdateMessage deliveryUpdateMessage = (DeliveryUpdateMessage) messageData;
    updateDeliveryMetaDataIfApplicable(deliveryUpdateMessage);
    if (ReceivingConstants.EVENT_TYPE_FINALIZED.equals(deliveryUpdateMessage.getEventType())) {
      deliveryEventService.processOpenInstuctionsAfterDeliveryFinalized(deliveryUpdateMessage);
      return;
    }
    preLabelDeliveryService.processDeliveryEvent(messageData);
  }

  private void updateDeliveryMetaDataIfApplicable(DeliveryUpdateMessage deliveryUpdateMessage) {
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
        ReceivingConstants.ENABLE_PUBLISH_UNLOAD_PROGRESS_AT_DELIVERY_COMPLETE)) {
      LOGGER.info(
          "Updated the deliveryMetadata for delivery = {} required for YMS2 Unload Progress ",
          deliveryUpdateMessage.getDeliveryNumber());
      accDeliveryMetaDataService.persistMetaData(deliveryUpdateMessage);
    }
  }
}
