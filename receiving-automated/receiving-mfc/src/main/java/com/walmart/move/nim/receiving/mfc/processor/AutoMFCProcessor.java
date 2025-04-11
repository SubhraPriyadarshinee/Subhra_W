package com.walmart.move.nim.receiving.mfc.processor;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Item;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ItemDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.service.AsyncPersister;
import com.walmart.move.nim.receiving.mfc.model.common.PalletInfo;
import com.walmart.move.nim.receiving.mfc.utils.MFCUtils;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class AutoMFCProcessor extends AbstractMFCDeliveryProcessor {
  private static final Logger LOGGER = LoggerFactory.getLogger(AutoMFCProcessor.class);

  @Autowired private AsyncPersister asyncPersister;

  @Override
  public void publishContainer(List<Container> containerList) {
    throw new ReceivingInternalException(
        ExceptionCodes.UNABLE_TO_PROCESS_INVENTORY, "Inventory publishing is not supported");
  }

  @Timed(
      name = "autoMFCDeliveryProcessingTimed",
      level1 = "uwms-receiving-api",
      level2 = "autoMFCProcessor")
  @ExceptionCounted(
      name = "autoMFCDeliveryProcessingExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "autoMFCProcessor")
  @Override
  public void processEvent(MessageData messageData) throws ReceivingException {

    if (!(messageData instanceof DeliveryUpdateMessage)) {
      LOGGER.warn("AutoMFC : Not appropirate message to process MFC need");
      return;
    }

    DeliveryUpdateMessage deliveryUpdateMessage = (DeliveryUpdateMessage) messageData;

    if (!isEventProcessable(deliveryUpdateMessage)) {
      LOGGER.warn(
          "AutoMFC : Unable to process deliveryEvent as not in right status = {} ",
          deliveryUpdateMessage);
      return;
    }

    LOGGER.info("AutoMFC : Got ShipmentUpdate Event from GDM = {}", deliveryUpdateMessage);

    if (Objects.nonNull(deliveryUpdateMessage.getDeliveryStatus())) {
      saveDeliveryMetadata(deliveryUpdateMessage);
      LOGGER.info("AutoMFC : Metadata Created");
    }

    if (!MFCUtils.isValidPreLabelEvent(deliveryUpdateMessage.getEventType())) {
      LOGGER.info("AutoMFC : Delivery status updated. Invalid pre-label event");
      return;
    }

    ASNDocument asnDocument = getASNDocument(deliveryUpdateMessage);
    Map<Long, ItemDetails> itemMap = super.createItemMap(asnDocument);

    BiFunction<Item, ItemDetails, Boolean> eligibleChecker =
        (item, itemDetails) ->
            StringUtils.equalsIgnoreCase(item.getReplenishmentCode(), "MARKET_FULFILLMENT_CENTER");

    Map<String, PalletInfo> palletInfoMap = MFCUtils.getPalletInfoMap(asnDocument.getPacks());
    for (Pack pack : asnDocument.getPacks()) {
      LOGGER.info("AutoMFC : ContainerCreation for pallet={} started", pack.getPalletNumber());
      try {
        super.processPacks(pack, asnDocument, itemMap, eligibleChecker, palletInfoMap);
      } catch (Exception ex) {
        LOGGER.error(
            "AutoMFC : Exception occurred while creating pallet={}", pack.getPalletNumber(), ex);
        asyncPersister.publishMetric(
            "autoMfcFailedPackCreation", "uwms-receiving", "auto-mfc", "autoMFC_processPack");

      } finally {
        LOGGER.info("AutoMFC : ContainerCreation for pallet={} finished", pack.getPalletNumber());
      }
      LOGGER.info(
          "AutoMFC : Got ShipmentUpdate Event from GDM = {} finished", deliveryUpdateMessage);
    }
  }
}
