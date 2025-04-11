package com.walmart.move.nim.receiving.endgame.service;

import static com.walmart.move.nim.receiving.endgame.common.AuditHelper.*;
import static com.walmart.move.nim.receiving.endgame.common.EndGameUtils.getDefaultSellerId;
import static com.walmart.move.nim.receiving.endgame.common.EndGameUtils.retrieveVendorInfos;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DC_PROPERTIES;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ENABLE_AUDIT_REQUIRED_FLAG;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ENABLE_SSOT_READ;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.FLUID_REPLEN_CASE_ENABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_NEW_ITEM;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ITEM_MDM_ITEM_NUMBER;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PURCHASE_ORDER_PARTITION_SIZE_KEY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.REPLEN;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TCL_MAX_PER_DELIVERY_KEY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TCL_MIN_PER_DELIVERY_KEY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.WM_BASE_DIVISION_CODE;
import static java.lang.Long.parseLong;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Objects.nonNull;
import static org.springframework.util.CollectionUtils.isEmpty;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.LabelType;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.EventStore;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByPoAndPoLineResponse;
import com.walmart.move.nim.receiving.core.model.audit.AuditFlagRequest;
import com.walmart.move.nim.receiving.core.model.audit.AuditFlagRequestItem;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrderLine;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.core.service.EventStoreService;
import com.walmart.move.nim.receiving.core.service.ItemMDMService;
import com.walmart.move.nim.receiving.core.service.LocationService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.endgame.common.AuditHelper;
import com.walmart.move.nim.receiving.endgame.common.DeliveryHelper;
import com.walmart.move.nim.receiving.endgame.common.EndGameUtils;
import com.walmart.move.nim.receiving.endgame.common.SlottingUtils;
import com.walmart.move.nim.receiving.endgame.config.EndgameManagedConfig;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.constants.LabelGenMode;
import com.walmart.move.nim.receiving.endgame.constants.LabelStatus;
import com.walmart.move.nim.receiving.endgame.model.DivertRequestItem;
import com.walmart.move.nim.receiving.endgame.model.EndGameLabelData;
import com.walmart.move.nim.receiving.endgame.model.EndGameSlottingData;
import com.walmart.move.nim.receiving.endgame.model.LabelRequestVO;
import com.walmart.move.nim.receiving.endgame.model.SlottingDivertRequest;
import com.walmart.move.nim.receiving.endgame.model.SlottingDivertResponse;
import com.walmart.move.nim.receiving.endgame.model.UpdateAttributes;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.EventStoreType;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.libs.google.common.collect.Lists;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Endgame specific delivery event processor. 1. Gets delivery document from GDM. 2. Generates TCLs
 * based on the document and send over Kafka to Hawkeye. 3. Get slotting related data and sends over
 * Kafka to Hawkeye.
 */
public class EndGameDeliveryEventProcessor implements EventProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndGameDeliveryEventProcessor.class);

  @Resource(name = EndgameConstants.ENDGAME_DELIVERY_SERVICE)
  private DeliveryService gdmService;

  @Autowired private EndGameLabelingService labelingService;
  @Autowired private EndGameSlottingService slottingService;
  @Autowired private ReceiptService receiptService;
  @Autowired private ItemMDMService itemMDMService;
  @Autowired private AuditHelper auditHelper;
  @Autowired private DeliveryHelper deliveryHelper;
  @Autowired private TenantSpecificConfigReader configUtils;
  @ManagedConfiguration private EndgameManagedConfig endgameManagedConfig;
  @Autowired private Gson gson;
  @Autowired private LocationService locationService;
  @Autowired private EventStoreService eventStoreService;

  @Override
  @Timed(name = "Endgame-PreGen", level1 = "uwms-receiving", level2 = "Endgame-PreGen")
  @ExceptionCounted(
      name = "Endgame-PreGen-Exception",
      level1 = "uwms-receiving",
      level2 = "Endgame-PreGen-Exception")
  @TimeTracing(component = AppComponent.ENDGAME, type = Type.MESSAGE, flow = "PreGen")
  public void processEvent(MessageData messageData) throws ReceivingException {
    if (!(messageData instanceof DeliveryUpdateMessage)) {
      LOGGER.error(ReceivingConstants.LOG_WRONG_MESSAGE_FORMAT, messageData);
      return;
    }
    DeliveryUpdateMessage deliveryUpdateMessage = (DeliveryUpdateMessage) messageData;
    Long deliveryNumber = parseLong(deliveryUpdateMessage.getDeliveryNumber());

    if (!ReceivingUtils.isValidPreLabelEvent(deliveryUpdateMessage.getEventType())
        || !ReceivingUtils.isValidStatus(
            DeliveryStatus.valueOf(deliveryUpdateMessage.getDeliveryStatus()))) {
      LOGGER.error(
          "Received event [event={}] event from GDM for [deliveryNumber={}] with [deliveryStatus={}]. Hence ignoring for pre-label generation",
          deliveryUpdateMessage.getEventType(),
          deliveryNumber,
          deliveryUpdateMessage.getDeliveryStatus());
      return;
    }

    LOGGER.info(
        "Received event [event={}] from GDM for [deliveryNumber={}] with [deliveryStatus={}]. Processing for pre-label generation",
        deliveryUpdateMessage.getEventType(),
        deliveryNumber,
        deliveryUpdateMessage.getDeliveryStatus());

    Delivery delivery = gdmService.getGDMData(deliveryUpdateMessage);
    if (StringUtils.isBlank(delivery.getDoorNumber())) {
      LOGGER.error(
          "Stop generating and sending TCLs when the door is not assign for  this [deliveryNumber={}]",
          deliveryNumber);
      return;
    }

    if (isFluidReplenEnabled()) {
      if (!isDoorEligibleForReceiving(deliveryUpdateMessage, delivery.getDoorNumber())) {
        LOGGER.warn(
            "Stop processing delivery as door is in Replen mode for this [doorNumber={}]",
            delivery.getDoorNumber());
        return;
      }
    }
    EndGameUtils.enrichDefaultSellerIdInPurchaseOrders(
        delivery.getPurchaseOrders(),
        endgameManagedConfig.getWalmartDefaultSellerId(),
        endgameManagedConfig.getSamsDefaultSellerId());
    updateOpenQuantityInDelivery(delivery);
    generateAndSendTCL(delivery, deliveryUpdateMessage.getEventType());
    Map<String, DivertRequestItem> upcItemMap = getItemMap(delivery, deliveryUpdateMessage);
    getAndSendDiverts(delivery, upcItemMap);
    if (isFluidReplenEnabled()) {
      updateDeliveryStatusInEventTable(delivery.getDoorNumber(), delivery.getDeliveryNumber());
    }
  }

  private boolean isFluidReplenEnabled() {
    return configUtils.getConfiguredFeatureFlag(FLUID_REPLEN_CASE_ENABLED);
  }

  private boolean isDoorEligibleForReceiving(
      DeliveryUpdateMessage deliveryUpdateMessage, String doorNumber) {
    JsonArray locationArray = locationService.getLocationInfoAsJsonArray(doorNumber);
    if (nonNull(locationArray)
        && !locationArray.isEmpty()
        && isReplenMode(locationArray.get(0).getAsJsonObject())) {
      // Save delivery to EVENT_STORE table
      saveAndUpdateDeliveryEvent(deliveryUpdateMessage, doorNumber);
      return false;
    }
    return true;
  }

  private boolean isReplenMode(JsonObject location) {
    return Objects.nonNull(location.get(ReceivingConstants.PROPERTIES))
        && Objects.nonNull(
            location
                .get(ReceivingConstants.PROPERTIES)
                .getAsJsonObject()
                .get(ReceivingConstants.MODE))
        && REPLEN.equalsIgnoreCase(
            location
                .get(ReceivingConstants.PROPERTIES)
                .getAsJsonObject()
                .get(ReceivingConstants.MODE)
                .getAsString());
  }

  private void updateDeliveryStatusInEventTable(String doorNumber, Long deliveryNumber) {
    int count =
        eventStoreService.updateEventStoreEntityStatusAndLastUpdatedDateByCriteria(
            doorNumber,
            deliveryNumber,
            EventTargetStatus.DELETE,
            EventStoreType.DOOR_ASSIGNMENT,
            new Date());
    LOGGER.info("Event Update count {}", count);
  }

  private void saveAndUpdateDeliveryEvent(
      DeliveryUpdateMessage deliveryUpdateMessage, String doorNumber) {
    Long deliveryNumber = Long.parseLong(deliveryUpdateMessage.getDeliveryNumber());
    int count =
        eventStoreService.updateEventStoreEntityStatusAndLastUpdatedDateByKeyOrDeliveryNumber(
            doorNumber,
            deliveryNumber,
            EventTargetStatus.DELETE,
            EventStoreType.DOOR_ASSIGNMENT,
            new Date());
    LOGGER.info("Previous Event Update count {} for door {}", count, doorNumber);
    EventStore eventStore =
        EventStore.builder()
            .deliveryNumber(deliveryNumber)
            .eventStoreKey(doorNumber)
            .eventStoreType(EventStoreType.DOOR_ASSIGNMENT)
            .status(EventTargetStatus.PENDING)
            .payload(gson.toJson(deliveryUpdateMessage))
            .retryCount(0)
            .build();
    eventStoreService.saveEventStoreEntity(eventStore);
  }

  public void getAndSendDiverts(Delivery delivery, Map<String, DivertRequestItem> upcItemMap) {
    SlottingDivertRequest slottingDivertRequest =
        SlottingUtils.populateSlottingDivertRequest(upcItemMap);
    SlottingDivertResponse slottingDivertResponse =
        slottingService.getDivertsFromSlotting(slottingDivertRequest);
    EndGameUtils.enrichDefaultSellerIdInSlottingDivertResponse(
        slottingDivertResponse.getDivertLocations(),
        getDefaultSellerId(
            getBaseDivisionCode(slottingDivertRequest),
            endgameManagedConfig.getWalmartDefaultSellerId(),
            endgameManagedConfig.getSamsDefaultSellerId()));
    EndGameSlottingData endgameSlottingData =
        SlottingUtils.populateEndgameSlottingData(
            slottingDivertResponse.getDivertLocations(),
            delivery.getDeliveryNumber(),
            delivery.getDoorNumber(),
            upcItemMap);
    slottingService.save(endgameSlottingData.getDestinations());
    slottingService.send(endgameSlottingData, delivery.getDeliveryNumber());
  }

  @TimeTracing(component = AppComponent.ENDGAME, type = Type.INTERNAL, executionFlow = "TCL-Gen")
  public void generateAndSendTCL(Delivery delivery, String deliveryEvent) {
    if (ReceivingUtils.isPOChangeEvent(deliveryEvent)) {
      LOGGER.debug(
          "Ignore generating and sending TCLs for received event [event={}] from GDM for [deliveryNumber={}]",
          deliveryEvent,
          delivery.getDeliveryNumber());
      return;
    }
    long quantity = EndGameUtils.calculateLabelCount(delivery);
    quantity = max(quantity, configUtils.getTCLMinValPerDelivery(TCL_MIN_PER_DELIVERY_KEY));
    quantity = min(quantity, configUtils.getTCLMaxValPerDelivery(TCL_MAX_PER_DELIVERY_KEY));
    LabelRequestVO labelRequestVO =
        LabelRequestVO.builder()
            .labelGenMode(LabelGenMode.AUTOMATED)
            .deliveryNumber(String.valueOf(delivery.getDeliveryNumber()))
            .door(delivery.getDoorNumber())
            .trailerId(delivery.getLoadInformation().getTrailerInformation().getTrailerId())
            .quantity(quantity)
            .type(LabelType.TCL)
            .carrierName(delivery.getCarrierName())
            .carrierScanCode(delivery.getLoadInformation().getTrailerInformation().getScacCode())
            .billCode(delivery.getPurchaseOrders().get(0).getFreightTermCode())
            .build();

    EndGameLabelData labelData = labelingService.generateLabel(labelRequestVO);
    labelingService.persistLabel(labelData);
    LOGGER.info(EndgameConstants.LOG_TCL_PERSISTED_SUCCESSFULLY, labelData.getDeliveryNumber());
    String response = labelingService.send(labelData);
    updateStatus(response, Long.valueOf(labelData.getDeliveryNumber()));
  }

  public Map<String, DivertRequestItem> getItemMap(
      Delivery delivery, DeliveryUpdateMessage deliveryUpdateMessage) {
    String poNumber = null;
    Integer poLineNumber = null;
    AuditFlagRequest auditFlagRequest = new AuditFlagRequest();
    Map<Long, AuditFlagRequestItem> auditFlagRequestItems = new HashMap<>();
    auditFlagRequest.setDeliveryNumber(delivery.getDeliveryNumber());

    if (ReceivingUtils.isPOChangeEvent(deliveryUpdateMessage.getEventType())) {
      MultiValueMap<String, String> queryParamDetails =
          UriComponentsBuilder.fromUriString(deliveryUpdateMessage.getUrl())
              .build()
              .getQueryParams();
      poNumber = queryParamDetails.get("docNbr").get(0);
      if (ReceivingUtils.isPOLineChangeEvent(deliveryUpdateMessage.getEventType())) {
        poLineNumber = Integer.valueOf(queryParamDetails.get("docLineNbr").get(0));
      }
    }

    // Set of itemNumbers to send an update for, to Hawkeye
    Set<Long> itemNumbers = new HashSet<>();
    List<PurchaseOrder> purchaseOrders =
        EndGameUtils.removeCancelledDocument(delivery.getPurchaseOrders());
    for (PurchaseOrder po : purchaseOrders) {
      if (Objects.isNull(poNumber) || po.getPoNumber().equals(poNumber)) {
        for (PurchaseOrderLine line : po.getLines()) {
          if (Objects.isNull(poLineNumber) || line.getPoLineNumber().equals(poLineNumber)) {
            Long item = line.getItemDetails().getNumber();
            Integer vendorNumber = retrieveVendorInfos(po, line);
            itemNumbers.add(item);
            if (nonNull(line.getVendor())) {
              auditFlagRequestItems.put(
                  item,
                  prepareAuditFlagRequestItem(
                      line,
                      vendorNumber,
                      EndGameUtils.isVnpkPalletItem(configUtils, line, po.getBaseDivisionCode())));
            }
          }
        }
      }
    }
    auditFlagRequest.setItems(new ArrayList<>(auditFlagRequestItems.values()));

    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, EndgameConstants.DEFAULT_USER);
    Map<String, Object> itemResponse =
        itemMDMService.retrieveItemDetails(
            itemNumbers,
            httpHeaders,
            EndGameUtils.getBaseDivisionCode(purchaseOrders),
            true,
            false);
    Map<String, Map<String, Object>> processedItemMap = new HashMap<>();
    List<Map<String, Object>> foundItems =
        (List<Map<String, Object>>) itemResponse.get(ReceivingConstants.ITEM_FOUND_SUPPLY_ITEM);

    Set<Long> newItemNumbers = new HashSet<>();
    foundItems.forEach(
        item -> prepareItemMapAndNewItemsList(item, processedItemMap, newItemNumbers));

    if (configUtils.getConfiguredFeatureFlag(ENABLE_SSOT_READ) && !newItemNumbers.isEmpty()) {
      LOGGER.info(
          "Enabled SSOT read and item update process started for [deliveryNumber={}]",
          delivery.getDeliveryNumber());
      try {
        deliveryHelper.processItemUpdateFromSSOT(
            newItemNumbers, httpHeaders, EndGameUtils.getBaseDivisionCode(purchaseOrders));
      } catch (Exception e) {
        LOGGER.error("Item Update from SSOT failed due to error: {}", e.getMessage());
      }
    }

    Map<Long, UpdateAttributes> auditInfo;

    if (configUtils.getConfiguredFeatureFlag(ENABLE_AUDIT_REQUIRED_FLAG)) {
      auditInfo = generateAndSaveAuditInfo(auditFlagRequest);
    } else {
      auditInfo = Collections.emptyMap();
    }
    return SlottingUtils.generateUPCDivertRequestMap(
        delivery.getPurchaseOrders(),
        processedItemMap,
        itemNumbers,
        endgameManagedConfig.getIsNewItemPath(),
        endgameManagedConfig.getAssortmentPath(),
        auditInfo);
  }

  private static void prepareItemMapAndNewItemsList(
      Map<String, Object> item,
      Map<String, Map<String, Object>> processedItemMap,
      Set<Long> newItemNumbers) {
    processedItemMap.put(item.get(ITEM_MDM_ITEM_NUMBER).toString(), item);
    Map<String, Object> dcProperties =
        ReceivingUtils.convertValue(
            item.get(DC_PROPERTIES), new TypeReference<Map<String, Object>>() {});
    if (nonNull(dcProperties) && Boolean.TRUE.equals(dcProperties.get(IS_NEW_ITEM))) {
      newItemNumbers.add(parseLong(item.get(ITEM_MDM_ITEM_NUMBER).toString()));
    }
  }

  private Map<Long, UpdateAttributes> generateAndSaveAuditInfo(AuditFlagRequest auditFlagRequest) {
    Map<String, Boolean> auditFlagResponseMap = auditHelper.fetchAndSaveAuditInfo(auditFlagRequest);

    Map<Long, UpdateAttributes> caseAuditInfo = new HashMap<>();
    auditFlagRequest
        .getItems()
        .forEach(
            requestItem ->
                caseAuditInfo.put(
                    requestItem.getItemNumber(),
                    UpdateAttributes.builder()
                        .isAuditEnabled(
                            auditFlagResponseMap.get(
                                prepareAuditKey(
                                    requestItem.getVendorNumber(), requestItem.getItemNumber())))
                        .build()));
    return caseAuditInfo;
  }

  /**
   * Add open quantity for each line in the delivery by (OrderQty - ReceivedQty).
   *
   * @param delivery the delivery
   */
  private void updateOpenQuantityInDelivery(Delivery delivery) {
    List<ReceiptSummaryQtyByPoAndPoLineResponse> qtyByPoAndPoLineList = new ArrayList<>();
    Lists.partition(
            delivery.getPurchaseOrders(),
            configUtils.getPurchaseOrderPartitionSize(PURCHASE_ORDER_PARTITION_SIZE_KEY))
        .stream()
        .map(
            purchaseOrders ->
                receiptService.getReceiptSummaryQtyByPOandPOLineResponse(
                    purchaseOrders, delivery.getDeliveryNumber()))
        .forEach(qtyByPoAndPoLineList::addAll);
    Map<String, Long> qtyByPoAndPoLineMap = new HashMap<>();
    for (ReceiptSummaryQtyByPoAndPoLineResponse qtyByPoAndPoLine : qtyByPoAndPoLineList) {
      String key =
          qtyByPoAndPoLine.getPurchaseReferenceNumber()
              + EndgameConstants.DELIM_DASH
              + qtyByPoAndPoLine.getPurchaseReferenceLineNumber();
      qtyByPoAndPoLineMap.put(key, qtyByPoAndPoLine.getReceivedQty());
    }

    delivery
        .getPurchaseOrders()
        .forEach(
            purchaseOrder ->
                purchaseOrder
                    .getLines()
                    .forEach(
                        line -> {
                          String key =
                              purchaseOrder.getPoNumber()
                                  + EndgameConstants.DELIM_DASH
                                  + line.getPoLineNumber();
                          if (nonNull(qtyByPoAndPoLineMap.get(key))) {
                            line.setOpenQuantity(
                                line.getOrdered().getQuantity()
                                    + line.getOvgThresholdLimit().getQuantity()
                                    - qtyByPoAndPoLineMap.get(key).intValue());
                          } else {
                            line.setOpenQuantity(
                                line.getOrdered().getQuantity()
                                    + line.getOvgThresholdLimit().getQuantity());
                          }
                        }));
  }

  private void updateStatus(String response, Long deliveryNumber) {
    if (EndgameConstants.SUCCESS_MSG.equalsIgnoreCase(response)) {
      labelingService.updateStatus(
          LabelStatus.GENERATED, LabelStatus.SENT, response, deliveryNumber);
    } else {
      labelingService.updateStatus(
          LabelStatus.GENERATED, LabelStatus.FAILED, response, deliveryNumber);
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE,
          String.format(
              ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG, EndgameConstants.TCL_UPLOAD_FLOW));
    }
  }

  private String getBaseDivisionCode(SlottingDivertRequest slottingDivertRequest) {
    if (!isEmpty(slottingDivertRequest.getDivertRequestItems())) {
      return slottingDivertRequest.getDivertRequestItems().get(0).getBaseDivisionCode();
    }
    return WM_BASE_DIVISION_CODE;
  }
}
