package com.walmart.move.nim.receiving.mfc.processor;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Item;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ItemDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.service.AsyncPersister;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.mfc.common.MFCConstant;
import com.walmart.move.nim.receiving.mfc.model.common.PalletInfo;
import com.walmart.move.nim.receiving.mfc.utils.MFCUtils;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ManualMFCProcessor extends AbstractMFCDeliveryProcessor {
  private static final Logger LOGGER = LoggerFactory.getLogger(ManualMFCProcessor.class);

  @Resource(name = "containerTransformer")
  private Transformer<Container, ContainerDTO> transformer;

  @Autowired private ContainerService containerService;

  @Autowired private AsyncPersister asyncPersister;

  @Timed(
      name = "manualMFCContainerPublishingTimed",
      level1 = "uwms-receiving-api",
      level2 = "manualMFCProcessor")
  @ExceptionCounted(
      name = "manualMFCContainerPublishingExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "manualMFCProcessor")
  @Override
  public void publishContainer(List<Container> containerList) {

    List<Container> containerToBePublished =
        containerList
            .stream()
            .map(
                container -> {
                  MFCUtils.replaceSSCCWithTrackingId(container);
                  return container;
                })
            .filter(
                container ->
                    !StringUtils.equalsIgnoreCase(
                        container.getInventoryStatus(), MFCConstant.YET_TO_PUBLISH))
            .collect(Collectors.toList());

    List<String> duplicateContainer =
        containerList
            .stream()
            .filter(
                container ->
                    StringUtils.equalsIgnoreCase(
                        container.getInventoryStatus(), MFCConstant.YET_TO_PUBLISH))
            .map(container -> container.getSsccNumber())
            .collect(Collectors.toList());
    if (!duplicateContainer.isEmpty()) {
      LOGGER.info(
          "Duplicate container detected and hence, not publishing to inventory . containers={}",
          duplicateContainer);
    }

    TenantContext.setAdditionalParams("kafkaHeaders:containerLocation", "MFC");
    containerService.publishMultipleContainersToInventory(
        transformer.transformList(containerToBePublished));
    LOGGER.info("ManualMFC : Container got published successfully to inventory");
  }

  @Timed(
      name = "manualMFCDeliveryProcessingTimed",
      level1 = "uwms-receiving-api",
      level2 = "manualMFCProcessor")
  @ExceptionCounted(
      name = "manualMFCDeliveryProcessingExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "manualMFCProcessor")
  @Override
  public void processEvent(MessageData messageData) throws ReceivingException {

    if (!(messageData instanceof DeliveryUpdateMessage)) {
      LOGGER.warn("ManualMFC : Not appropirate message to process MFC need");
      return;
    }

    DeliveryUpdateMessage deliveryUpdateMessage = (DeliveryUpdateMessage) messageData;

    if (!isEventProcessable(deliveryUpdateMessage)) {
      LOGGER.warn(
          "ManualMFC : Unable to process deliveryEvent as not in right status = {} ",
          deliveryUpdateMessage);
      return;
    }

    LOGGER.info("ManualMFC : Got ShipmentUpdate Event from GDM = {}", deliveryUpdateMessage);

    if (Objects.nonNull(deliveryUpdateMessage.getDeliveryStatus())) {
      saveDeliveryMetadata(deliveryUpdateMessage);
      LOGGER.info("ManualMFC : Metadata Created");
    }

    if (!MFCUtils.isValidPreLabelEvent(deliveryUpdateMessage.getEventType())) {
      LOGGER.info("ManualMFC : Delivery status updated. Invalid pre-label event");
      return;
    }

    ASNDocument asnDocument = getASNDocument(deliveryUpdateMessage);
    Map<Long, ItemDetails> itemMap = super.createItemMap(asnDocument);

    BiFunction<Item, ItemDetails, Boolean> eligibleChecker =
        (item, itemDetails) ->
            StringUtils.equalsIgnoreCase(item.getReplenishmentCode(), "MARKET_FULFILLMENT_CENTER")
                && Objects.nonNull(itemDetails)
                && (boolean) itemDetails.getItemAdditonalInformation().get("mfcEnabled");

    Set<String> trackingIds = new HashSet<>();
    List<Container> containers = new ArrayList<>();
    Map<String, PalletInfo> palletInfoMap = MFCUtils.getPalletInfoMap(asnDocument.getPacks());
    for (Pack pack : asnDocument.getPacks()) {
      try {

        LOGGER.info("ManualMFC : ContainerCreation for pallet={} started", pack.getPalletNumber());
        trackingIds.add(
            super.processPacks(pack, asnDocument, itemMap, eligibleChecker, palletInfoMap)
                .getTrackingId());

      } catch (Exception ex) {
        LOGGER.error(
            "ManualMFC : Exception occured while creating pallet={}", pack.getPalletNumber(), ex);
        asyncPersister.publishMetric(
            "manualMfcFailedPackCreation", "uwms-receiving", "manual-mfc", "manualMFC_processPack");

      } finally {
        LOGGER.info("ManualMFC : ContainerCreation for pallet={} finished", pack.getPalletNumber());
      }
    }

    // TODO This needs to be optimized
    trackingIds
        .stream()
        .forEach(
            id -> {
              try {
                containers.add(containerService.getContainerByTrackingId(id));
              } catch (ReceivingException e) {
                LOGGER.error("Unable to fetch containers for trackingId = {}", id, e);
              }
            });

    if (!containers.isEmpty()) {
      this.publishContainer(containers);
    }
    LOGGER.info(
        "ManualMFC : Got ShipmentUpdate Event from GDM = {} finished", deliveryUpdateMessage);
  }
}
