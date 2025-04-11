package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.libs.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class MccDeliveryEventProcessor implements EventProcessor {
  private static final Logger LOGGER = LoggerFactory.getLogger(MccDeliveryEventProcessor.class);
  @Autowired private DeliveryEventService deliveryEventService;

  @Override
  public void processEvent(MessageData messageData) throws ReceivingException {
    if (!(messageData instanceof DeliveryUpdateMessage)) {
      LOGGER.error("Message format is wrong {}.", messageData);
      return;
    }
    DeliveryUpdateMessage deliveryUpdateMessage = (DeliveryUpdateMessage) messageData;
    if (StringUtils.equals(
        ReceivingConstants.EVENT_TYPE_FINALIZED, deliveryUpdateMessage.getEventType())) {
      deliveryEventService.processOpenInstuctionsAfterDeliveryFinalized(deliveryUpdateMessage);
    }
  }
}
