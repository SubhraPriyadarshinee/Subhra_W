package com.walmart.move.nim.receiving.acc.service;

import static com.walmart.move.nim.receiving.core.message.common.HawkeyeItemUpdateType.*;
import static com.walmart.move.nim.receiving.core.model.label.RejectReason.*;

import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.message.common.ActiveDeliveryMessage;
import com.walmart.move.nim.receiving.core.message.common.HawkeyeItemUpdateType;
import com.walmart.move.nim.receiving.core.message.common.ItemUpdateEventType;
import com.walmart.move.nim.receiving.core.message.common.ItemUpdateMessage;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.label.RejectReason;
import com.walmart.move.nim.receiving.core.service.LabelDataService;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ACCItemUpdateProcessor implements EventProcessor {
  private static final Logger LOGGER = LoggerFactory.getLogger(ACCItemUpdateProcessor.class);
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private ACCItemUpdateService itemUpdateService;
  @Autowired private PreLabelDeliveryService preLabelDeliveryService;
  @Autowired private LabelDataService labelDataService;

  @Override
  public void processEvent(MessageData messageData) throws ReceivingException {
    ItemUpdateMessage itemUpdateMessage = (ItemUpdateMessage) messageData;
    Integer itemNumber = itemUpdateMessage.getItemNumber();

    HawkeyeItemUpdateType eventType = getEventType(itemUpdateMessage);
    if (Objects.equals(eventType, INVALID_ITEM_UPDATE)) {
      LOGGER.warn(
          "Invalid item update event: {} received for item: {} and facility: {}, skipping this message: {}",
          itemUpdateMessage.getEventType(),
          itemNumber,
          TenantContext.getFacilityNum(),
          itemUpdateMessage);
      return;
    }

    Map<Long, DeliveryDetails> deliveryDetailsMap = new HashMap<>();
    for (ActiveDeliveryMessage activeDeliveryMessage : itemUpdateMessage.getActiveDeliveries()) {
      Long deliveryNumber = activeDeliveryMessage.getDeliveryNumber();
      DeliveryDetails deliveryDetails =
          preLabelDeliveryService.fetchDeliveryDetails(
              activeDeliveryMessage.getUrl(), deliveryNumber);
      if (Objects.isNull(deliveryDetails)) {
        LOGGER.info(
            "Failed to fetch delivery: {}. Hence, ignoring item update event: {} for item: {}",
            deliveryNumber,
            eventType.name(),
            itemNumber);
        continue;
      }
      filterDeliveryDetailsByItemNumberAndEventType(deliveryDetails, itemNumber, eventType);
      if (CollectionUtils.isNotEmpty(deliveryDetails.getDeliveryDocuments())) {
        deliveryDetailsMap.put(deliveryNumber, deliveryDetails);
      } else {
        LOGGER.warn(
            "no valid PO exists for item: {} and item update event: {} in delivery: {}",
            itemNumber,
            eventType.name(),
            deliveryNumber);
      }
    }
    if (deliveryDetailsMap.isEmpty()) {
      LOGGER.warn(
          "no valid active deliveries exists for item: {} for item update event: {}, skipping this message: {}",
          itemNumber,
          eventType.name(),
          itemUpdateMessage);
      return;
    }
    itemUpdateService.processItemUpdateEvent(itemUpdateMessage, deliveryDetailsMap, eventType);
  }

  /**
   * GDM will send generic event types to downstream. Receiving will determine the specific event
   * types, depending on the 'from' and 'to' values from the payload
   *
   * @param itemUpdateMessage message sent by upstream (GDM)
   * @return event type for item update to be published to downstream
   */
  private HawkeyeItemUpdateType getEventType(ItemUpdateMessage itemUpdateMessage) {
    ItemUpdateEventType eventType = ItemUpdateEventType.valueOf(itemUpdateMessage.getEventType());
    switch (eventType) {
      case HANDLING_CODE_UPDATE:
        return getHandlingCodeUpdateEvent(itemUpdateMessage);
      case CATALOG_GTIN_UPDATE:
        return getCatalogUpdateEvent(itemUpdateMessage);
      case CHANNEL_FLIP:
        return getChannelFlipEvent(itemUpdateMessage);
      default:
        return INVALID_ITEM_UPDATE;
    }
  }

  private HawkeyeItemUpdateType getHandlingCodeUpdateEvent(ItemUpdateMessage itemUpdateMessage) {
    String from = itemUpdateMessage.getFrom();
    String to = itemUpdateMessage.getTo();
    if ((isConveyable(from) || Objects.isNull(from)) && !isConveyable(to)) {
      return CONVEYABLE_TO_NONCON_GLOBAL;
    } else if ((!isConveyable(from) || Objects.isNull(from)) && isConveyable(to)) {
      return NONCON_TO_CONVEYABLE_GLOBAL;
    } else {
      return INVALID_ITEM_UPDATE;
    }
  }

  private HawkeyeItemUpdateType getCatalogUpdateEvent(ItemUpdateMessage itemUpdateMessage) {
    String to = itemUpdateMessage.getTo();
    if (StringUtils.isBlank(to) || ReceivingConstants.EMPTY_CATALOG_GTIN.equals(to)) {
      return UNDO_CATALOG_GLOBAL;
    } else if (!StringUtils.isBlank(to)) {
      return UPC_CATALOG_GLOBAL;
    } else {
      return INVALID_ITEM_UPDATE;
    }
  }

  private HawkeyeItemUpdateType getChannelFlipEvent(ItemUpdateMessage itemUpdateMessage) {
    String from = itemUpdateMessage.getFrom();
    String to = itemUpdateMessage.getTo();
    if (ACCConstants.CROSSU.equalsIgnoreCase(from) && ACCConstants.SSTKU.equalsIgnoreCase(to)) {
      return CROSSU_TO_SSTKU_DELIVERY;
    } else if (ACCConstants.SSTKU.equalsIgnoreCase(from)
        && ACCConstants.CROSSU.equalsIgnoreCase(to)) {
      return SSTKU_TO_CROSSU_DELIVERY;
    } else {
      return INVALID_ITEM_UPDATE;
    }
  }

  boolean isConveyable(String handlingCode) {
    return ReceivingConstants.EMPTY_STRING.equals(handlingCode)
        || CollectionUtils.emptyIfNull(appConfig.getHandlingCodesForConveyableIndicator())
            .contains(handlingCode);
  }

  private void filterDeliveryDetailsByItemNumberAndEventType(
      DeliveryDetails deliveryDetails, Integer itemNumber, HawkeyeItemUpdateType eventType) {

    List<DeliveryDocument> deliveryDocuments =
        CollectionUtils.emptyIfNull(deliveryDetails.getDeliveryDocuments())
            .stream()
            .map(
                deliveryDocument -> {
                  Long deliveryNumber = deliveryDocument.getDeliveryNumber();
                  List<DeliveryDocumentLine> filteredLines =
                      CollectionUtils.emptyIfNull(deliveryDocument.getDeliveryDocumentLines())
                          .stream()
                          .filter(
                              deliveryDocumentLine ->
                                  checkIfPOLineIsValidForItemUpdateEvent(
                                      deliveryDocumentLine, deliveryNumber, itemNumber, eventType))
                          .collect(Collectors.toList());
                  deliveryDocument.setDeliveryDocumentLines(filteredLines);
                  return deliveryDocument;
                })
            .filter(
                deliveryDocument ->
                    CollectionUtils.isNotEmpty(deliveryDocument.getDeliveryDocumentLines()))
            .collect(Collectors.toList());
    deliveryDetails.setDeliveryDocuments(deliveryDocuments);
  }

  private boolean checkIfPOLineIsValidForItemUpdateEvent(
      DeliveryDocumentLine deliveryDocumentLine,
      Long deliveryNumber,
      Integer itemNumber,
      HawkeyeItemUpdateType eventType) {

    if (!Objects.equals(deliveryDocumentLine.getItemNbr(), Long.valueOf(itemNumber))) {
      return false;
    }
    boolean isDAFreight =
        InstructionUtils.isDAFreight(
            deliveryDocumentLine.getPurchaseRefType(),
            deliveryDocumentLine.getActiveChannelMethods());

    switch (eventType) {
      case CONVEYABLE_TO_NONCON_GLOBAL:
      case NONCON_TO_CONVEYABLE_GLOBAL:
        return isDAFreight;
      case SSTKU_TO_CROSSU_DELIVERY:
      case CROSSU_TO_SSTKU_DELIVERY:
        return checkIfPOLineIsValidForChannelFlip(deliveryDocumentLine, deliveryNumber, eventType);
      default:
        return true;
    }
  }

  private boolean checkIfPOLineIsValidForChannelFlip(
      DeliveryDocumentLine deliveryDocumentLine,
      Long deliveryNumber,
      HawkeyeItemUpdateType eventType) {

    List<RejectReason> expectedRejectReasons =
        getExpectedRejectReasonsForChannelFlip(deliveryDocumentLine, eventType);
    Collection<LabelData> labelDataList =
        CollectionUtils.emptyIfNull(
            labelDataService.findAllLabelDataByDeliveryPOPOL(
                deliveryNumber,
                deliveryDocumentLine.getPurchaseReferenceNumber(),
                deliveryDocumentLine.getPurchaseReferenceLineNumber()));
    if (CollectionUtils.isEmpty(labelDataList)) {
      return true;
    }
    labelDataList =
        labelDataList
            .stream()
            .filter(
                labelData ->
                    checkIfLabelDataIsValidForChannelFlip(labelData, expectedRejectReasons))
            .collect(Collectors.toList());
    return !CollectionUtils.isEmpty(labelDataList);
  }

  private List<RejectReason> getExpectedRejectReasonsForChannelFlip(
      DeliveryDocumentLine deliveryDocumentLine, HawkeyeItemUpdateType eventType) {

    boolean isConveyable = deliveryDocumentLine.getIsConveyable();
    if (SSTKU_TO_CROSSU_DELIVERY.equals(eventType)) {
      return isConveyable ? Collections.emptyList() : Arrays.asList(NONCON_DA, NONCON_DA_FLIP);
    } else {
      return isConveyable
          ? Collections.singletonList(CONVEYABLE_SSTK)
          : Arrays.asList(NONCON_SSTK, NONCON_SSTK_FLIP);
    }
  }

  private boolean checkIfLabelDataIsValidForChannelFlip(
      LabelData labelData, List<RejectReason> expectedRejectReasons) {

    if (CollectionUtils.isEmpty(expectedRejectReasons)
        && Objects.isNull(labelData.getRejectReason())) {
      return false;
    }
    return !expectedRejectReasons.contains(labelData.getRejectReason());
  }
}
