package com.walmart.move.nim.receiving.acc.service;

import com.walmart.move.nim.receiving.acc.util.ACCUtils;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.ItemCatalogUpdateLog;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.message.common.HawkeyeItemUpdateType;
import com.walmart.move.nim.receiving.core.message.common.ItemUpdateInstructionMessage;
import com.walmart.move.nim.receiving.core.message.common.ItemUpdateMessage;
import com.walmart.move.nim.receiving.core.message.publisher.MessagePublisher;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.label.RejectReason;
import com.walmart.move.nim.receiving.core.repositories.ItemCatalogRepository;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ACCItemUpdateService {
  private static final Logger LOGGER = LoggerFactory.getLogger(ACCItemUpdateService.class);
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private ItemCatalogRepository itemCatalogRepository;

  public void processItemUpdateEvent(
      ItemUpdateMessage itemUpdateMessage,
      Map<Long, DeliveryDetails> deliveryDetailsMap,
      HawkeyeItemUpdateType eventType)
      throws ReceivingException {

    Integer itemNumber = itemUpdateMessage.getItemNumber();
    LOGGER.info("Processing item update event: {} for item: {}", eventType.name(), itemNumber);
    switch (eventType) {
      case UNDO_CATALOG_DELIVERY:
      case UPC_CATALOG_GLOBAL:
      case UPC_CATALOG_DELIVERY:
      case UNDO_CATALOG_GLOBAL:
        publishInstructionForCatalogUpdate(itemUpdateMessage, deliveryDetailsMap, eventType);
        break;
      case NONCON_TO_CONVEYABLE_GLOBAL:
        publishLabelsForItemUpdate(itemUpdateMessage, deliveryDetailsMap, eventType);
        break;
      case CONVEYABLE_TO_NONCON_GLOBAL:
      case CROSSU_TO_SSTKU_DELIVERY:
        publishInstructionForItemUpdate(itemUpdateMessage, deliveryDetailsMap, eventType);
        break;
      case SSTKU_TO_CROSSU_DELIVERY:
        processItemUpdateEventForSSTKUtoCROSSU(itemUpdateMessage, deliveryDetailsMap, eventType);
        break;
      default:
        LOGGER.warn(
            "Invalid item update event: {} received for item: {} and facility: {}",
            eventType.name(),
            itemNumber,
            TenantContext.getFacilityNum());
        break;
    }
  }

  private void processItemUpdateEventForSSTKUtoCROSSU(
      ItemUpdateMessage itemUpdateMessage,
      Map<Long, DeliveryDetails> deliveryDetailsMap,
      HawkeyeItemUpdateType eventType)
      throws ReceivingException {

    DeliveryDetails deliveryDetails = deliveryDetailsMap.values().stream().findFirst().get();
    Boolean isConveyable =
        deliveryDetails
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getIsConveyable();
    if (Boolean.TRUE.equals(isConveyable)) {
      publishLabelsForItemUpdate(itemUpdateMessage, deliveryDetailsMap, eventType);
    } else {
      publishInstructionForItemUpdate(itemUpdateMessage, deliveryDetailsMap, eventType);
    }
  }

  private void publishLabelsForItemUpdate(
      ItemUpdateMessage itemUpdateMessage,
      Map<Long, DeliveryDetails> deliveryDetailsMap,
      HawkeyeItemUpdateType eventType)
      throws ReceivingException {

    for (Map.Entry<Long, DeliveryDetails> entry : deliveryDetailsMap.entrySet()) {
      Long deliveryNumber = entry.getKey();
      DeliveryDetails deliveryDetails = entry.getValue();
      LOGGER.info(
          "Generating labels for item: {} in delivery: {} and item update event: {}",
          itemUpdateMessage.getItemNumber(),
          deliveryNumber,
          eventType.name());
      String doorNumber = deliveryDetails.getDoorNumber();
      String trailer = deliveryDetails.getTrailerId();
      Map<DeliveryDocumentLine, List<LabelData>> refreshedLabelData =
          updateLabelDataTable(deliveryDetails, eventType);
      tenantSpecificConfigReader
          .getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.LABEL_GENERATOR_SERVICE,
              GenericLabelGeneratorService.class)
          .publishLabelsToAcl(
              refreshedLabelData, deliveryNumber, doorNumber, trailer, Boolean.TRUE);
    }
  }

  private void publishInstructionForItemUpdate(
      ItemUpdateMessage itemUpdateMessage,
      Map<Long, DeliveryDetails> deliveryDetailsMap,
      HawkeyeItemUpdateType eventType)
      throws ReceivingException {

    Integer itemNumber = itemUpdateMessage.getItemNumber();
    ItemUpdateInstructionMessage itemUpdateInstructionMessage = new ItemUpdateInstructionMessage();
    itemUpdateInstructionMessage.setItemNumber(itemNumber);
    List<Long> deliveryList = new ArrayList<>();
    for (Map.Entry<Long, DeliveryDetails> entry : deliveryDetailsMap.entrySet()) {
      Long deliveryNumber = entry.getKey();
      DeliveryDetails deliveryDetails = entry.getValue();
      LOGGER.info(
          "Received item update event: {} for item: {} in delivery: {} for publishing instruction.",
          eventType.name(),
          itemNumber,
          deliveryNumber);
      updateLabelDataTable(deliveryDetails, eventType);
      deliveryList.add(deliveryNumber);
    }
    if (Objects.equals(eventType, HawkeyeItemUpdateType.SSTKU_TO_CROSSU_DELIVERY)
        || Objects.equals(eventType, HawkeyeItemUpdateType.CROSSU_TO_SSTKU_DELIVERY)) {
      itemUpdateInstructionMessage.setDeliveryNumber(deliveryList);
    }
    DeliveryDetails deliveryDetails = deliveryDetailsMap.values().stream().findFirst().get();
    Optional<RejectReason> optionalRejectReason =
        Optional.ofNullable(
            ACCUtils.getRejectReasonForItemUpdate(
                deliveryDetails.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0),
                eventType));
    if (optionalRejectReason.isPresent()) {
      RejectReason rejectReason = optionalRejectReason.get();
      itemUpdateInstructionMessage.setRejectCode(rejectReason.getRejectCode());
      publishMessage(itemUpdateInstructionMessage, eventType);
    } else {
      LOGGER.error(
          "Reject reason is null so, ignoring publishing instruction for item: {} and event: {}",
          itemNumber,
          eventType.name());
    }
  }

  private void publishInstructionForCatalogUpdate(
      ItemUpdateMessage itemUpdateMessage,
      Map<Long, DeliveryDetails> deliveryDetailsMap,
      HawkeyeItemUpdateType eventType) {

    Integer itemNumber = itemUpdateMessage.getItemNumber();
    ItemUpdateInstructionMessage itemUpdateInstructionMessage = new ItemUpdateInstructionMessage();
    itemUpdateInstructionMessage.setItemNumber(itemNumber);

    switch (eventType) {
      case UPC_CATALOG_GLOBAL:
        processCatalogUpdateEvent(itemUpdateMessage, eventType);
        break;
      case UNDO_CATALOG_GLOBAL:
        processUndoCatalogEvent(itemUpdateMessage, deliveryDetailsMap, eventType);
        break;
      default:
        LOGGER.warn(
            "Invalid catalog update event: {} received for item: {}", eventType.name(), itemNumber);
        break;
    }
  }

  private void processCatalogUpdateEvent(
      ItemUpdateMessage itemUpdateMessage, HawkeyeItemUpdateType eventType) {

    Integer itemNumber = itemUpdateMessage.getItemNumber();
    ItemUpdateInstructionMessage itemUpdateInstructionMessage = new ItemUpdateInstructionMessage();
    itemUpdateInstructionMessage.setItemNumber(itemNumber);
    itemUpdateInstructionMessage.setCatalogGTIN(itemUpdateMessage.getTo());

    publishMessage(itemUpdateInstructionMessage, eventType);
  }

  private void processUndoCatalogEvent(
      ItemUpdateMessage itemUpdateMessage,
      Map<Long, DeliveryDetails> deliveryDetailsMap,
      HawkeyeItemUpdateType eventType) {

    Integer itemNumber = itemUpdateMessage.getItemNumber();
    ItemUpdateInstructionMessage itemUpdateInstructionMessage = new ItemUpdateInstructionMessage();
    itemUpdateInstructionMessage.setItemNumber(itemNumber);

    updateItemCatalogUpdateLogTable(itemUpdateMessage, deliveryDetailsMap);
    publishMessage(itemUpdateInstructionMessage, eventType);
  }

  private Map<DeliveryDocumentLine, List<LabelData>> updateLabelDataTable(
      DeliveryDetails deliveryDetails, HawkeyeItemUpdateType eventType) throws ReceivingException {

    return tenantSpecificConfigReader
        .getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.LABEL_GENERATOR_SERVICE,
            GenericLabelGeneratorService.class)
        .updateLabelDataForItemUpdate(deliveryDetails, eventType, Boolean.TRUE);
  }

  private void updateItemCatalogUpdateLogTable(
      ItemUpdateMessage itemUpdateMessage, Map<Long, DeliveryDetails> deliveryDetailsMap) {

    Integer itemNumber = itemUpdateMessage.getItemNumber();
    String previousCatalogGtin = itemUpdateMessage.getFrom();
    String currentCatalogGtin = itemUpdateMessage.getTo();
    ItemCatalogUpdateLog itemCatalogUpdateLog =
        ItemCatalogUpdateLog.builder()
            .itemNumber(Long.valueOf(itemNumber))
            .deliveryNumber(deliveryDetailsMap.keySet().stream().findFirst().get())
            .oldItemUPC(previousCatalogGtin)
            .newItemUPC(
                StringUtils.isBlank(currentCatalogGtin)
                    ? ReceivingConstants.EMPTY_STRING
                    : currentCatalogGtin)
            .createUserId("sysadmin")
            .build();
    saveItemCatalogUpdateLog(itemCatalogUpdateLog);
  }

  @Transactional
  @InjectTenantFilter
  public ItemCatalogUpdateLog saveItemCatalogUpdateLog(ItemCatalogUpdateLog itemCatalogUpdateLog) {
    return itemCatalogRepository.save(itemCatalogUpdateLog);
  }

  private void publishMessage(
      ItemUpdateInstructionMessage message, HawkeyeItemUpdateType eventType) {
    Map<String, Object> headers = new HashMap<>();
    headers.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, TenantContext.getCorrelationId());
    headers.put(ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum().toString());
    headers.put(ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode());
    headers.put(ReceivingConstants.EVENT_TYPE, eventType.name());

    MessagePublisher<MessageData> itemUpdateInstructionPublisher =
        tenantSpecificConfigReader.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.ITEM_UPDATE_INSTRUCTION_PUBLISHER,
            MessagePublisher.class);
    itemUpdateInstructionPublisher.publish(message, headers);
  }
}
