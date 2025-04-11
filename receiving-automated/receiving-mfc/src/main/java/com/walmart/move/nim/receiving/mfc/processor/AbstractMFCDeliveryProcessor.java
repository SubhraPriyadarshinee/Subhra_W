package com.walmart.move.nim.receiving.mfc.processor;

import com.walmart.move.nim.receiving.core.common.OverageType;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Item;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ItemDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.utils.CoreUtil;
import com.walmart.move.nim.receiving.mfc.common.MFCConstant;
import com.walmart.move.nim.receiving.mfc.common.StoreDeliveryStatus;
import com.walmart.move.nim.receiving.mfc.model.common.PalletInfo;
import com.walmart.move.nim.receiving.mfc.service.MFCContainerService;
import com.walmart.move.nim.receiving.mfc.service.MFCDeliveryMetadataService;
import com.walmart.move.nim.receiving.mfc.service.MFCDeliveryService;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.*;
import java.util.function.BiFunction;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;

public abstract class AbstractMFCDeliveryProcessor implements EventProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractMFCDeliveryProcessor.class);

  @Resource(name = MFCConstant.MFC_DELIVERY_SERVICE)
  private MFCDeliveryService deliveryService;

  @Resource(name = MFCConstant.MFC_DELIVERY_METADATA_SERVICE)
  private MFCDeliveryMetadataService mfcDeliveryMetadataService;

  @Autowired private MFCContainerService mfcContainerService;

  @Autowired private ContainerPersisterService containerPersisterService;

  @Timed(
      name = "mfcASNDownloadTimed",
      level1 = "uwms-receiving-api",
      level2 = "abstractDeliveryProcessor")
  @ExceptionCounted(
      name = "mfcASNDownloadExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "abstractDeliveryProcessor")
  protected ASNDocument getASNDocument(DeliveryUpdateMessage deliveryUpdateMessage) {
    return deliveryService.getGDMData(
        deliveryUpdateMessage.getDeliveryNumber(), deliveryUpdateMessage.getShipmentDocumentId());
  }

  public abstract void publishContainer(List<Container> containerList);

  public Boolean isEventProcessable(DeliveryUpdateMessage deliveryUpdateMessage) {
    if (ObjectUtils.isEmpty(deliveryUpdateMessage.getEventType())) return false;
    return Arrays.asList(
            ReceivingConstants.EVENT_DELIVERY_SCHEDULED,
            ReceivingConstants.EVENT_DELIVERY_SHIPMENT_ADDED,
            ReceivingConstants.EVENT_DELIVERY_ARRIVED)
        .contains(deliveryUpdateMessage.getEventType());
  }

  public DeliveryMetaData saveDeliveryMetadata(DeliveryUpdateMessage deliveryUpdateMessage) {
    DeliveryMetaData deliveryMetaData =
        mfcDeliveryMetadataService
            .findByDeliveryNumber(deliveryUpdateMessage.getDeliveryNumber())
            .orElse(
                DeliveryMetaData.builder()
                    .deliveryNumber(deliveryUpdateMessage.getDeliveryNumber())
                    .build());

    if (Objects.nonNull(deliveryMetaData.getDeliveryStatus())
        && !StoreDeliveryStatus.isValidDeliveryStatusForUpdate(
            StoreDeliveryStatus.getDeliveryStatus(deliveryMetaData.getDeliveryStatus()),
            StoreDeliveryStatus.getDeliveryStatus(deliveryUpdateMessage.getDeliveryStatus()))) {
      LOGGER.info(
          "Current delivery status is {} hence ignore delivery update for status {}",
          deliveryMetaData.getDeliveryStatus(),
          deliveryUpdateMessage.getDeliveryStatus());
      return deliveryMetaData;
    }
    deliveryMetaData.setDeliveryStatus(
        DeliveryStatus.valueOf(deliveryUpdateMessage.getDeliveryStatus()));
    LOGGER.info(
        "Going to store the meta-data for deliveryNumber = {}",
        deliveryUpdateMessage.getDeliveryNumber());
    return mfcDeliveryMetadataService.save(deliveryMetaData);
  }

  public Map<Long, ItemDetails> createItemMap(ASNDocument asnDocument) {
    return CoreUtil.getItemMap(asnDocument);
  }

  @Timed(
      name = "mfcContainerCreationTimed",
      level1 = "uwms-receiving-api",
      level2 = "abstractDeliveryProcessor")
  @ExceptionCounted(
      name = "mfcContainerCreationExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "abstractDeliveryProcessor")
  protected Container processPacks(
      Pack pack,
      ASNDocument asnDocument,
      Map<Long, ItemDetails> itemMap,
      BiFunction<Item, ItemDetails, Boolean> eligibleChecker,
      Map<String, PalletInfo> palletInfoMap) {

    Container createdContainer = null;

    Container _container =
        mfcContainerService.createContainer(
            null, asnDocument, pack, itemMap, eligibleChecker, OverageType.UKNOWN, palletInfoMap);
    if (!Objects.isNull(_container)) {
      LOGGER.info(
          "Going to persist the container into DB for palletId = {} packNumber={}",
          pack.getPalletNumber(),
          pack.getPackNumber());
      createdContainer = containerPersisterService.saveContainer(_container);
    }

    return createdContainer;
  }
}
