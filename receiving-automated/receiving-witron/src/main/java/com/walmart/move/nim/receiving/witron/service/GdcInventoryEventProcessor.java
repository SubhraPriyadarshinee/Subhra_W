package com.walmart.move.nim.receiving.witron.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.conversionToVendorPack;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.sanitize;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.witron.common.GdcUtil.isValidInventoryAdjustment;
import static java.util.Objects.nonNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigApiClient;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateRequest;
import com.walmart.move.nim.receiving.core.model.InventoryAdjustmentTO;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.DefaultUpdateContainerQuantityRequestHandler;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.common.GDCFlagReader;
import io.strati.configuration.annotation.ManagedConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;

/**
 * Witron Specific processor for events received from Inventory System
 *
 * @author vn50o7n
 */
public class GdcInventoryEventProcessor implements EventProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(GdcInventoryEventProcessor.class);

  @Autowired private WitronContainerService witronContainerService;
  @Autowired private ContainerService containerService;
  @Autowired private WitronSplitPalletService witronSplitPalletService;
  @Autowired private GDCFlagReader gdcFlagReader;
  @Autowired @Lazy private ItemConfigApiClient itemConfig;
  @ManagedConfiguration private AppConfig appConfig;

  @Autowired
  private DefaultUpdateContainerQuantityRequestHandler updateContainerQuantityRequestHandler;

  @Override
  public void processEvent(MessageData messageData) throws ReceivingException {
    Integer facilityNum = TenantContext.getFacilityNum();
    if (appConfig.getKafkaInventoryAdjustmentListenerEnabledFacilities().contains(facilityNum)) {
      LOGGER.info(
          "Secure Kafka inventory adjustment listener is enabled for facility: {}, skipping this MQ adjustment message",
          facilityNum);
      clear();
      return;
    }
    processInventoryAdjustmentTo((InventoryAdjustmentTO) messageData);
  }

  private void processInventoryAdjustmentTo(InventoryAdjustmentTO invAdjTo)
      throws ReceivingException {
    final JsonObject invAdj = invAdjTo.getJsonObject();
    LOGGER.info(
        "Received inventory adjustment message={}",
        invAdjTo.getJsonObject().toString().replace("\n", " "));
    if (!isValidInventoryAdjustment(invAdj)) {
      LOGGER.info("Invalid Inventory Adjustment for Gdc message=");
      clear();
      return;
    }

    JsonObject eventObject = invAdj.getAsJsonObject(INVENTORY_ADJUSTMENT_EVENT_OBJECT);

    if (!(eventObject.has(INVENTORY_ADJUSTMENT_ITEM_LIST)
        && eventObject.getAsJsonArray(INVENTORY_ADJUSTMENT_ITEM_LIST).size() > 0
        && eventObject.has(INVENTORY_ADJUSTMENT_TRACKING_ID))) {
      LOGGER.info("Invalid Inventory Adjustment for Gdc message. Missing itemList or trackingId");
      clear();
      return;
    }

    String trackingId = sanitize(eventObject.get(INVENTORY_ADJUSTMENT_TRACKING_ID).getAsString());
    final boolean isManualGdc = gdcFlagReader.isManualGdcEnabled();
    final boolean isOneAtlas = gdcFlagReader.isDCOneAtlasEnabled();
    final boolean isFullGls = isManualGdc && !isOneAtlas;
    if (isFullGls) {
      LOGGER.info("Ignore Inventory Adjustment for FullGls Gdc trackingId={})", trackingId);
      clear();
      return;
    }
    JsonObject itemList =
        (JsonObject) eventObject.getAsJsonArray(INVENTORY_ADJUSTMENT_ITEM_LIST).get(0);
    HttpHeaders headers = invAdjTo.getHttpHeaders();

    // Ignore the adjustments originated from Receiving
    final String requestOriginator = headers.get(REQUEST_ORIGINATOR).get(0);
    if (gdcFlagReader.isIgnoreAdjFromInventory()
        && (APP_NAME_VALUE.equalsIgnoreCase(requestOriginator)
            || RECEIVING.equalsIgnoreCase(requestOriginator))) {
      LOGGER.info(
          "Ignoring inventory adjustment because requestOriginator:{} for trackingId:{}",
          requestOriginator,
          trackingId);
      clear();
      return;
    }

    if (itemList.has(INVENTORY_ADJUSTMENT_ADJUSTMENT_TO)) {

      Long itemNumber = itemList.get(ITEM_NUMBER).getAsLong();
      final StringBuilder itemState = new StringBuilder("");
      if (isManualGdc
          && itemConfig.isOneAtlasNotConvertedItem(true, itemState, itemNumber, headers)) {
        LOGGER.info(
            "Ignore Inventory Adjustment for Gdc trackingId={} as itemNumber{} is OneAtlasItemNotConverted, isManualGdc is true",
            trackingId,
            itemNumber);
        clear();
        return;
      }

      JsonObject adjustmentTO = itemList.getAsJsonObject(INVENTORY_ADJUSTMENT_ADJUSTMENT_TO);
      if (adjustmentTO.has(INVENTORY_ADJUSTMENT_REASON_CODE)) {
        // TODO covert to switch from if/else
        int reasonCode = adjustmentTO.get(INVENTORY_ADJUSTMENT_REASON_CODE).getAsInt();
        if (reasonCode == VDM_REASON_CODE) {
          LOGGER.info("Valid VDM message received with trackingId:{}", trackingId);
          containerService.processVendorDamageAdjustment(trackingId, adjustmentTO, headers);
        } else if (reasonCode == RCS_CONCEALED_SHORTAGE_REASON_CODE
            || reasonCode == RCO_CONCEALED_OVERAGE_REASON_CODE) {
          LOGGER.info(
              "Valid CONCEALED SHORTAGE OR OVERAGE message received with trackingId:{}",
              trackingId);
          containerService.processConcealedShortageOrOverageAdjustment(
              trackingId, adjustmentTO, headers);
        } else {
          int availableToSellQty;
          switch (reasonCode) {
            case DAMAGE_REASON_CODE:
            case CONCEALED_DAMAGE_REASON_CODE:
              String damageQty = adjustmentTO.get(INVENTORY_ADJUSTMENT_QTY).toString();
              LOGGER.info(
                  "Valid Damage message received with trackingId:{}, damageQty:{}",
                  trackingId,
                  damageQty);
              containerService.processDamageAdjustment(
                  trackingId, Integer.parseInt(damageQty), headers);
              break;
            case INVENTORY_RECEIVING_CORRECTION_REASON_CODE:
              LOGGER.info("process ReceivingCorrection for reasonCode{}", reasonCode);
              final String availableToSellQtyUOM =
                  itemList.get(INVENTORY_AVAILABLE_TO_SELL_QTY_UOM).getAsString();
              final int vnpkRatio = itemList.get(INVENTORY_VNPK_RATIO).getAsInt();
              availableToSellQty = itemList.get(INVENTORY_AVAILABLE_TO_SELL_QTY).getAsInt();
              processReceivingCorrection(
                  trackingId,
                  adjustmentTO,
                  headers,
                  availableToSellQty,
                  availableToSellQtyUOM,
                  vnpkRatio);
              break;
            case VTR_REASON_CODE:
              final int adjustmentToQty = adjustmentTO.get(INVENTORY_ADJUSTMENT_QTY).getAsInt();
              final String adjustmentToQtyUOM =
                  adjustmentTO.get(INVENTORY_ADJUSTMENT_QTY_UOM).getAsString();
              availableToSellQty = itemList.get(INVENTORY_AVAILABLE_TO_SELL_QTY).getAsInt();
              LOGGER.info(
                  "processing VTR for reasonCode={}, quantity={} {}, availableToSellQty={}",
                  reasonCode,
                  adjustmentToQty,
                  adjustmentToQtyUOM,
                  availableToSellQty);
              witronContainerService.processVTR(
                  trackingId, headers, adjustmentToQty, adjustmentToQtyUOM, availableToSellQty);
              break;
            default:
              LOGGER.info(
                  "Ignoring inventory adjustment because invalid reasonCode={}", reasonCode);
          }
        }
      } else if (adjustmentTO.has(ReceivingConstants.INVENTORY_ADJUSTMENT_TARGET_CONTAINERS)) {
        Integer availableToSellQty =
            Integer.parseInt(itemList.get(INVENTORY_AVAILABLE_TO_SELL_QTY).toString());
        Integer adjustQty = Integer.parseInt(adjustmentTO.get(INVENTORY_ADJUSTMENT_QTY).toString());
        final JsonObject targetContainer =
            adjustmentTO
                .get(INVENTORY_ADJUSTMENT_TARGET_CONTAINERS)
                .getAsJsonArray()
                .get(0)
                .getAsJsonObject();
        final String newContainerTrackingId =
            targetContainer.get(INVENTORY_ADJUSTMENT_TRACKING_ID).getAsString();
        final JsonElement jsonElement = targetContainer.get(INVENTORY_ADJUSTMENT_CONTAINER_TYPE);
        String newContainerType = null;
        if (nonNull(jsonElement)) {
          newContainerType = jsonElement.getAsString();
        }
        LOGGER.info(
            "Valid split pallet request with originalContainer:{} availableToSellQty:{} newContainer:{} adjustBy:{}",
            trackingId,
            availableToSellQty,
            newContainerTrackingId,
            adjustQty);

        witronSplitPalletService.splitPallet(
            trackingId,
            availableToSellQty,
            newContainerTrackingId,
            newContainerType,
            adjustQty,
            headers);
      }
    }
  }

  private void clear() {
    TenantContext.clear();
  }

  private void processReceivingCorrection(
      String trackingId,
      JsonObject adjustmentTO,
      HttpHeaders headers,
      int availableToSellQty,
      String availableToSellQtyUOM,
      int vnpkRatio)
      throws ReceivingException {
    final String requestOriginator_inv = headers.getFirst(REQUEST_ORIGINATOR);
    if (APP_NAME_VALUE.equalsIgnoreCase(requestOriginator_inv)
        || RECEIVING.equalsIgnoreCase(requestOriginator_inv)) {
      LOGGER.info(
          "Ignoring inventory adjustment because `receiving' already processed ReceivingCorrection");
      return; // do nothing
    }

    ContainerUpdateRequest request_RCV = new ContainerUpdateRequest();
    request_RCV.setInventoryReceivingCorrection(true);
    final int adjustmentToQty_Inv = adjustmentTO.get(INVENTORY_ADJUSTMENT_QTY).getAsInt();
    final String adjustmentToQtyUOM_Inv =
        adjustmentTO.get(INVENTORY_ADJUSTMENT_QTY_UOM).getAsString();

    // Inv qty will be the new Quantity(adjustQuantity) in RCV
    final Integer newQtyVNPK =
        conversionToVendorPack(availableToSellQty, availableToSellQtyUOM, vnpkRatio, null);
    request_RCV.setAdjustQuantity(newQtyVNPK);
    Integer diffQtyVNPK =
        conversionToVendorPack(adjustmentToQty_Inv, adjustmentToQtyUOM_Inv, vnpkRatio, null);
    // old Quantity likely
    final int oldQtyVNPK = newQtyVNPK - diffQtyVNPK;
    // set new Qty as VNPK
    request_RCV.setInventoryQuantity(oldQtyVNPK);

    LOGGER.info("Initiating Inventory Receiving Correction for request={}", request_RCV);
    updateContainerQuantityRequestHandler.updateQuantityByTrackingId(
        trackingId, request_RCV, headers);
  }
}
