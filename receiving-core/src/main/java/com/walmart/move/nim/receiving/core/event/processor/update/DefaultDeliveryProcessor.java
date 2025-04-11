package com.walmart.move.nim.receiving.core.event.processor.update;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.repositories.DeliveryMetaDataRepository;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * This class extends BaseDeliveryProcessor to get the delivery from GDM and persist to
 * Delivery_meta_data table
 */
public class DefaultDeliveryProcessor extends BaseDeliveryProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDeliveryProcessor.class);
  @Autowired private DeliveryMetaDataRepository deliveryMetaDataRepository;

  @Override
  @Timed(
      name = "Default-Delivery-Processor",
      level1 = "uwms-receiving",
      level2 = "Default-Delivery-Processor")
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.MESSAGE,
      flow = "DefaultDeliveryProcessor")
  @ExceptionCounted(
      name = "Default-Delivery-Processor-Exception",
      level1 = "uwms-receiving",
      level2 = "Default-Delivery-Processor-Exception")
  public void processEvent(MessageData messageData) {
    if (!(messageData instanceof DeliveryUpdateMessage)) {
      LOGGER.error(ReceivingConstants.LOG_WRONG_MESSAGE_FORMAT, messageData);
      return;
    }
    DeliveryUpdateMessage deliveryUpdateMessage = (DeliveryUpdateMessage) messageData;
    Long deliveryNumber = Long.parseLong(deliveryUpdateMessage.getDeliveryNumber());
    if (!deliveryUpdateMessage.getEventType().equals(ReceivingConstants.EVENT_DOOR_ASSIGNED)
        || !ReceivingUtils.isValidStatus(
            DeliveryStatus.valueOf(deliveryUpdateMessage.getDeliveryStatus()))) {
      LOGGER.error(
          "Received: {} event from GDM for DeliveryNumber: {} with {} status. Hence ignoring for pre-label generation",
          deliveryUpdateMessage.getEventType(),
          deliveryNumber,
          deliveryUpdateMessage.getDeliveryStatus());
      return;
    }
    Optional<DeliveryMetaData> deliveryMetaData =
        deliveryMetaDataRepository.findByDeliveryNumber(String.valueOf(deliveryNumber));
    if (deliveryMetaData.isPresent()) {
      LOGGER.info(
          "Delivery: {} is already present in DELIVERY_METADATA table, so skipping this update",
          deliveryNumber);
      return;
    } else {
      Delivery delivery = super.getDelivery(deliveryUpdateMessage);
      LOGGER.info(
          "Persisting delivery: {} information into DELIVERY_METADATA table", deliveryNumber);
      super.createMetaData(delivery);
    }
  }
}
