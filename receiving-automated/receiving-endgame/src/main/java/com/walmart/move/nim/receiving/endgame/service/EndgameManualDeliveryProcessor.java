package com.walmart.move.nim.receiving.endgame.service;

import static com.walmart.move.nim.receiving.endgame.common.EndGameUtils.getBaseDivisionCode;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ENABLE_SSOT_READ;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_NEW_ITEM;
import static java.util.Objects.nonNull;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.event.processor.update.BaseDeliveryProcessor;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ItemDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrderLine;
import com.walmart.move.nim.receiving.core.repositories.DeliveryMetaDataRepository;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.endgame.common.DeliveryHelper;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

public class EndgameManualDeliveryProcessor extends BaseDeliveryProcessor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(EndgameManualDeliveryProcessor.class);

  @Resource(name = EndgameConstants.ENDGAME_DELIVERY_SERVICE)
  private DeliveryService gdmService;

  @Autowired private DeliveryMetaDataRepository deliveryMetaDataRepository;
  @Autowired private TenantSpecificConfigReader configUtils;
  @Autowired DeliveryHelper deliveryHelper;

  @Override
  public void processEvent(MessageData messageData) throws ReceivingException {
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

    if (configUtils.getConfiguredFeatureFlag(ENABLE_SSOT_READ)) {
      Delivery delivery = gdmService.getGDMData(deliveryUpdateMessage);
      Set<Long> newItemNumbers =
          delivery
              .getPurchaseOrders()
              .stream()
              .flatMap(purchaseOrder -> purchaseOrder.getLines().stream())
              .map(PurchaseOrderLine::getItemDetails)
              .filter(
                  itemDetails ->
                      nonNull(itemDetails.getAdditionalInformation())
                          && nonNull(itemDetails.getAdditionalInformation().get(IS_NEW_ITEM))
                          && Boolean.parseBoolean(
                              itemDetails.getAdditionalInformation().get(IS_NEW_ITEM).toString()))
              .map(ItemDetails::getNumber)
              .collect(Collectors.toSet());
      HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
      httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, EndgameConstants.DEFAULT_USER);
      LOGGER.info(
          "Enabled SSOT read and item update process started for [deliveryNumber={}]",
          delivery.getDeliveryNumber());
      try {
        deliveryHelper.processItemUpdateFromSSOT(
            newItemNumbers, httpHeaders, getBaseDivisionCode(delivery.getPurchaseOrders()));
      } catch (Exception e) {
        LOGGER.error("Item Update from SSOT failed due to error: {}", e.getMessage());
      }
    }
  }
}
