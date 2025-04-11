package com.walmart.move.nim.receiving.core.transformer;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.MFC_MESSAGE_CODE;

import com.walmart.move.nim.receiving.core.common.BusinessTransactionType;
import com.walmart.move.nim.receiving.core.common.LabelDataConstants;
import com.walmart.move.nim.receiving.core.common.ProducerIdentifier;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.ei.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InventoryTransformer {

  private static final Logger log = LoggerFactory.getLogger(InventoryTransformer.class);
  @ManagedConfiguration AppConfig appConfig;

  /**
   * Transform to Inventory
   *
   * @param consolidatedContainer
   * @param transformType
   * @return
   * @throws Exception
   */
  public InventoryDetails transformToInventory(
      Container consolidatedContainer, String transformType) {
    InventoryDetails inventoryDetails = new InventoryDetails();
    inventoryDetails.setInventory(Collections.singletonList(new Inventory()));
    Inventory inventory = inventoryDetails.getInventory().get(0);
    inventory.setEventInfo(prepareEventInfo(transformType));
    inventory.setWhseAreaCode(1);
    inventory.setQuantity(prepareInventoryQuantity(consolidatedContainer, transformType));
    if (consolidatedContainer.isOfflineRcv()
        || (Objects.nonNull(consolidatedContainer.getLabelType())
            && (ReceivingConstants.XDK1.equals(consolidatedContainer.getLabelType())
                || ReceivingConstants.XDK2.equals(consolidatedContainer.getLabelType())))) {
      inventory.setChannelType(ReceivingConstants.CROSSDOCK);
      log.info(
          "Label type : {} , inventory channel type is set as : {}, for tracking id : {}",
          consolidatedContainer.getLabelType(),
          inventory.getChannelType(),
          consolidatedContainer.getTrackingId());
    } else {
      inventory.setChannelType(ReceivingConstants.DIST_CHANNEL_TYPE);
    }
    inventory.setItemIdentifier(prepareItemIdentifier(consolidatedContainer, transformType));
    inventory.setIdempotentKey(generateIdempotentKey(consolidatedContainer));
    inventory.setTrackingNumber(consolidatedContainer.getTrackingId());
    if (!(ReceivingConstants.DC_SHIP_VOID.equalsIgnoreCase(transformType)
        || ReceivingConstants.DC_TRUE_OUT.equalsIgnoreCase(transformType)
        || ReceivingConstants.DC_XDK_VOID.equalsIgnoreCase(transformType)
        || (Objects.nonNull(consolidatedContainer.getLabelType())
            && (ReceivingConstants.XDK1.equals(consolidatedContainer.getLabelType())
                || ReceivingConstants.XDK2.equals(consolidatedContainer.getLabelType()))))) {
      inventory.setDocuments(prepareDocuments(consolidatedContainer));
    }
    inventory.setNodes(prepareNodes(consolidatedContainer, transformType));
    if (ReceivingConstants.DC_PICKS.equalsIgnoreCase(transformType)
        || ReceivingConstants.DC_VOID.equalsIgnoreCase(transformType)
        || ReceivingConstants.DC_SHIP_VOID.equalsIgnoreCase(transformType)
        || ReceivingConstants.DC_TRUE_OUT.equalsIgnoreCase(transformType)
        || ReceivingConstants.DC_XDK_VOID.equalsIgnoreCase(transformType)) {

      if (Objects.nonNull(consolidatedContainer.getContainerMiscInfo())
          && ReceivingConstants.MFC.equalsIgnoreCase(
              (String)
                  consolidatedContainer.getContainerMiscInfo().get(ReceivingConstants.DEST_TYPE))) {
        inventory.setMessageCode(MFC_MESSAGE_CODE);
      } else {
        inventory.setMessageCode(0);
      }
      log.info(
          "Label type is : {} , and inventory message code is set to : {}, for tracking id : {}",
          consolidatedContainer.getLabelType(),
          inventory.getMessageCode(),
          consolidatedContainer.getTrackingId());
    }
    return inventoryDetails;
  }

  /**
   * Preparation of EventInfo
   *
   * @param transformType
   * @return
   */
  private EventInfo prepareEventInfo(String transformType) {
    EventInfo eventInfo = new EventInfo();
    eventInfo.setProducerIdentifier(
        ProducerIdentifier.mapProducerIdentifier(transformType).getValue());
    ZonedDateTime dateTime =
        new Date().toInstant().atZone(ZoneId.of(ReceivingConstants.UTC_TIME_ZONE));
    eventInfo.setEventFromCreationTs(dateTime);
    eventInfo.setEventFromTimeZone(ReceivingConstants.UTC_TIME_ZONE);
    eventInfo.setEventReceivedTs(ZonedDateTime.now(Clock.systemUTC()));
    eventInfo.setCorelationId(
        Objects.isNull(TenantContext.getCorrelationId())
            ? UUID.randomUUID().toString()
            : TenantContext.getCorrelationId());

    return eventInfo;
  }

  /**
   * Preparation of InventoryQuantity
   *
   * @param consolidatedContainer
   * @param transformType
   * @return
   */
  private InventoryQuantity prepareInventoryQuantity(
      Container consolidatedContainer, String transformType) {
    ContainerItem containerItem = consolidatedContainer.getContainerItems().get(0);
    InventoryQuantity inventoryQuantity = new InventoryQuantity();
    inventoryQuantity.setInvTypeInd(ReceivingConstants.TURN_INV_TYPE_IND);
    inventoryQuantity.setVnpkWgtFormatCode(ReceivingConstants.VNPK_WGT_FORMAT_CODE);
    inventoryQuantity.setAvgCaseWgtQty(ReceivingConstants.AVG_CASE_WGT_QTY);
    inventoryQuantity.setUom(containerItem.getQuantityUOM());
    inventoryQuantity.setValue(containerItem.getQuantity());
    if (ReceivingConstants.DC_VOID.equalsIgnoreCase(transformType)
        || ReceivingConstants.DC_SHIP_VOID.equalsIgnoreCase(transformType)
        || ReceivingConstants.DC_TRUE_OUT.equalsIgnoreCase(transformType)
        || ReceivingConstants.DC_XDK_VOID.equalsIgnoreCase(transformType)) {
      inventoryQuantity.setValue(-containerItem.getQuantity());
    }
    return inventoryQuantity;
  }

  /**
   * Preparation of ItemIdentifier
   *
   * @param consolidatedContainer
   * @param transformType
   * @return
   */
  private ItemIdentifier prepareItemIdentifier(
      Container consolidatedContainer, String transformType) {
    ContainerItem containerItem = consolidatedContainer.getContainerItems().get(0);
    ItemIdentifier itemIdentifier = new ItemIdentifier();
    itemIdentifier.setValue(prepareItemIdentifierValue(consolidatedContainer));
    itemIdentifier.setItemNbr(containerItem.getItemNumber());
    if (ReceivingConstants.DC_PICKS.equalsIgnoreCase(transformType)
        || ReceivingConstants.DC_VOID.equalsIgnoreCase(transformType)
        || ReceivingConstants.DC_SHIP_VOID.equalsIgnoreCase(transformType)
        || ReceivingConstants.DC_TRUE_OUT.equalsIgnoreCase(transformType)
        || ReceivingConstants.DC_XDK_VOID.equalsIgnoreCase(transformType)) {
      itemIdentifier.setRequestedItemNbr(0);
    }
    String purchaseCompanyId =
        Objects.nonNull(containerItem.getPurchaseCompanyId())
            ? ReceivingConstants.PURCHASE_COMPANY_ID_PREFIX.concat(
                containerItem.getPurchaseCompanyId().toString())
            : null;
    itemIdentifier.setPuchaseCompanyId(Integer.parseInt(purchaseCompanyId));
    return itemIdentifier;
  }

  /**
   * Preparation of item identifier value
   *
   * @param consolidatedContainer
   * @return
   */
  private String prepareItemIdentifierValue(Container consolidatedContainer) {
    ContainerItem containerItem = consolidatedContainer.getContainerItems().get(0);
    String value;
    String itemUpc = StringUtils.trimToEmpty(containerItem.getItemUPC());
    if (itemUpc.length() >= 13) {
      value = StringUtils.leftPad(itemUpc.substring(0, 13), ReceivingConstants.GTIN14_LENGTH, '0');
    } else {
      value = itemUpc;
    }
    return value;
  }

  /**
   * Preparation of Documents
   *
   * @param consolidatedContainer
   * @return
   */
  private Documents prepareDocuments(Container consolidatedContainer) {
    ContainerItem containerItem = consolidatedContainer.getContainerItems().get(0);
    Document document = new Document();
    document.setBusinessTransactionRefDocumentNumber(containerItem.getPurchaseReferenceNumber());
    document.setBusinessTransactionSubType(containerItem.getPoTypeCode());
    BusinessTransactionType businessTransactionType =
        BusinessTransactionType.mapBusinessTransactionType(containerItem.getPoTypeCode());
    document.setBusinessTransactionType(businessTransactionType.getTransType());
    Documents documents = new Documents();
    documents.setDocument(Collections.singletonList(document));
    return documents;
  }

  /**
   * Preparation of Nodes
   *
   * @param consolidatedContainer
   * @param transformType
   * @return
   */
  private Nodes prepareNodes(Container consolidatedContainer, String transformType) {
    if (ReceivingConstants.DC_PICKS.equalsIgnoreCase(transformType)
        || ReceivingConstants.DC_VOID.equalsIgnoreCase(transformType)
        || ReceivingConstants.DC_SHIP_VOID.equalsIgnoreCase(transformType)
        || ReceivingConstants.DC_TRUE_OUT.equalsIgnoreCase(transformType)
        || ReceivingConstants.DC_XDK_VOID.equalsIgnoreCase(transformType)) {
      return getDCPicksNodes(consolidatedContainer);
    } else {
      return getDCReceivingNodes(consolidatedContainer);
    }
  }

  /**
   * Preparation of DC Picks Nodes
   *
   * @param consolidatedContainer
   * @return
   */
  private Nodes getDCPicksNodes(Container consolidatedContainer) {
    Node fromNode = new Node();
    fromNode.setNodeId(consolidatedContainer.getFacilityNum());
    fromNode.setNodeDiv(appConfig.getEiSourceNodeDivisionCode());
    fromNode.setNodeCountry(ReceivingConstants.COUNTRY_CODE_US);

    Node toNode = new Node();
    toNode.setNodeId(consolidatedContainer.getFacilityNum());
    toNode.setNodeDiv(appConfig.getEiSourceNodeDivisionCode());
    toNode.setNodeCountry(ReceivingConstants.COUNTRY_CODE_US);

    Node destinationNode = new Node();
    if (MapUtils.isNotEmpty(consolidatedContainer.getDestination())) {
      String buId =
          consolidatedContainer.getDestination().get(LabelDataConstants.LABEL_FIELD_BU_NUMBER);
      destinationNode.setNodeId(Objects.nonNull(buId) ? Integer.valueOf(buId) : null);
      destinationNode.setNodeDiv(appConfig.getEiDestinationNodeDivisionCode());
      destinationNode.setNodeCountry(
          consolidatedContainer
              .getDestination()
              .getOrDefault(LabelDataConstants.LABEL_FIELD_COUNTRY_CODE, "")
              .toUpperCase());
    }
    Nodes nodes = new Nodes();
    nodes.setFromNode(fromNode);
    nodes.setToNode(toNode);
    nodes.setDestinationNode(destinationNode);
    return nodes;
  }

  /**
   * Preparation of DC Receiving Nodes
   *
   * @param consolidatedContainer
   * @return
   */
  private Nodes getDCReceivingNodes(Container consolidatedContainer) {
    Node toNode = new Node();
    toNode.setNodeId(consolidatedContainer.getFacilityNum());
    toNode.setNodeDiv(appConfig.getEiSourceNodeDivisionCode());
    toNode.setNodeCountry(ReceivingConstants.COUNTRY_CODE_US);
    Node destinationNode = new Node();
    if (MapUtils.isNotEmpty(consolidatedContainer.getDestination())) {
      destinationNode.setNodeId(-1);
      destinationNode.setNodeDiv(appConfig.getEiDestinationNodeDivisionCode());
    }
    Nodes nodes = new Nodes();
    nodes.setToNode(toNode);
    nodes.setDestinationNode(destinationNode);
    return nodes;
  }

  /**
   * Generate Idempotent Key
   *
   * @param consolidatedContainer
   * @return
   */
  private String generateIdempotentKey(Container consolidatedContainer) {
    String siteNumber =
        StringUtils.leftPad(
            consolidatedContainer.getFacilityNum().toString(),
            ReceivingConstants.STORE_NUMBER_MAX_LENGTH,
            "0");
    String siteCountryCode =
        StringUtils.trimToEmpty(consolidatedContainer.getFacilityCountryCode()).toUpperCase();
    String trackingId = consolidatedContainer.getTrackingId();
    return new StringBuilder()
        .append(siteCountryCode)
        .append(siteNumber)
        .append(trackingId)
        .toString();
  }
}
