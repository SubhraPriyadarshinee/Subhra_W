package com.walmart.move.nim.receiving.core.event.processor.update;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.repositories.DeliveryMetaDataRepository;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/** This class handles BaseDeliveryProcessor to save delivery_metadata table */
public abstract class BaseDeliveryProcessor implements EventProcessor {
  private static final Logger LOGGER = LoggerFactory.getLogger(BaseDeliveryProcessor.class);

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE_V3)
  private DeliveryService deliveryService;

  @Autowired private DeliveryMetaDataRepository deliveryMetaDataRepository;

  /**
   * Get the delivery information from GDM
   *
   * @param deliveryUpdateMessage
   */
  public Delivery getDelivery(DeliveryUpdateMessage deliveryUpdateMessage) {
    LOGGER.info(
        "Getting delivery information from GDM: {}", deliveryUpdateMessage.getDeliveryNumber());
    try {
      return deliveryService.getGDMData(deliveryUpdateMessage);
    } catch (ReceivingException ex) {
      throw new ReceivingInternalException(
          ExceptionCodes.GDM_ERROR,
          String.format(ReceivingConstants.UNABLE_TO_GET_DELIVERY_FROM_GDM));
    }
  }

  /**
   * persisting to delivery_meta_data from delivery information
   *
   * @param delivery
   */
  public DeliveryMetaData createMetaData(Delivery delivery) {
    LOGGER.info(
        "Creating DELIVERY_METADATA information for delivery: {}", delivery.getDeliveryNumber());
    DeliveryMetaData deliveryMetaData =
        DeliveryMetaData.builder()
            .deliveryNumber(String.valueOf(delivery.getDeliveryNumber()))
            .deliveryStatus(DeliveryStatus.valueOf(delivery.getStatusInformation().getStatus()))
            .doorNumber(delivery.getDoorNumber())
            .trailerNumber(delivery.getLoadInformation().getTrailerInformation().getTrailerId())
            .carrierScacCode(delivery.getLoadInformation().getTrailerInformation().getScacCode())
            .carrierName(delivery.getCarrierName())
            .billCode(delivery.getPurchaseOrders().get(0).getFreightTermCode())
            .build();
    deliveryMetaDataRepository.save(deliveryMetaData);
    return deliveryMetaData;
  }
}
