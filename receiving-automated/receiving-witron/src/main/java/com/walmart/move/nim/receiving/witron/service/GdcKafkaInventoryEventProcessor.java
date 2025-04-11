package com.walmart.move.nim.receiving.witron.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.conversionToVendorPack;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigApiClient;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateRequest;
import com.walmart.move.nim.receiving.core.model.InventoryAdjustmentTO;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.DefaultUpdateContainerQuantityRequestHandler;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.common.GDCFlagReader;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service(ReceivingConstants.GDC_KAFKA_INVENTORY_EVENT_PROCESSOR)
public class GdcKafkaInventoryEventProcessor implements EventProcessor {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(GdcKafkaInventoryEventProcessor.class);
  @Autowired private GDCFlagReader gdcFlagReader;
  @Autowired private ContainerService containerService;
  @Autowired private WitronSplitPalletService witronSplitPalletService;
  @Autowired @Lazy private ItemConfigApiClient itemConfig;

  @Autowired
  private DefaultUpdateContainerQuantityRequestHandler updateContainerQuantityRequestHandler;

  @Autowired private WitronContainerService witronContainerService;

  @Override
  public void processEvent(MessageData messageData) throws ReceivingException {

    InventoryAdjustmentTO invAdjTo = (InventoryAdjustmentTO) messageData;
    LOGGER.info(
        "Received Kafka inventory adjustment message={}", invAdjTo.getJsonObject().toString());
    final JsonObject invAdj = invAdjTo.getJsonObject();
    HttpHeaders headers = invAdjTo.getHttpHeaders();

    try {
      if (!isValidInventoryEventForReceiving(headers)
          || !isValidInventoryEventMessageForReceiving(invAdj)) {
        LOGGER.info("Invalid Kafka Inventory Adjustment message for Gdc ");
        clear();
        return;
      }
      JsonObject eventDataJson = invAdj.getAsJsonObject(INVENTORY_ADJUSTMENT_EVENT_DATA);
      JsonObject eventJson = eventDataJson.getAsJsonObject(INVENTORY_ADJUSTMENT_EVENT);
      JsonObject payloadJson = invAdj.getAsJsonObject(INVENTORY_PAYLOAD);
      String trackingId = eventJson.get(INVENTORY_ADJUSTMENT_TRACKING_ID).getAsString();

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

      final boolean isManualGdc = gdcFlagReader.isManualGdcEnabled();
      final boolean isOneAtlas = gdcFlagReader.isDCOneAtlasEnabled();
      final boolean isFullGls = isManualGdc && !isOneAtlas;
      if (isFullGls) {
        LOGGER.info("Ignore Inventory Adjustment for FullGls Gdc trackingId={})", trackingId);
        clear();
        return;
      }

      final String itemNumberStr =
          payloadJson
              .get(INVENTORY_LINES)
              .getAsJsonArray()
              .get(0)
              .getAsJsonObject()
              .get(ITEM_NUMBER)
              .getAsString();
      final StringBuilder itemState = new StringBuilder("");
      if (isManualGdc
          && itemConfig.isOneAtlasNotConvertedItem(
              true, itemState, Long.parseLong(itemNumberStr), headers)) {
        LOGGER.info(
            "Ignore Inventory Adjustment for Gdc trackingId={} as itemNumber=[{}] is OneAtlasItemNotConverted and isManualGdc",
            trackingId,
            itemNumberStr);
        clear();
        return;
      }

      String deltaQty =
          eventJson
              .getAsJsonArray(INVENTORY_UPDATED_ITEMS)
              .get(0)
              .getAsJsonObject()
              .getAsJsonObject(INVENTORY_QUANTITY_CHANGE_AND_ITEM_STATE)
              .getAsJsonObject(INVENTORY_QUANTITY_CHANGE)
              .get(INVENTORY_DELTAQTY)
              .getAsString();

      int availableToSellQty =
          payloadJson
              .getAsJsonArray(INVENTORY_LINES)
              .get(0)
              .getAsJsonObject()
              .get(INVENTORY_AVAILABLE_TO_SELL)
              .getAsInt();

      if (headers.get(FLOW_NAME).contains(ADJUSTMENT_FLOW)
          && eventJson.has(INVENTORY_ADJUSTMENT_REASON_CODE)
          && !StringUtils.isEmpty(
              eventJson.getAsJsonObject(INVENTORY_ADJUSTMENT_REASON_CODE).get(MOVE_TYPE_CODE))) {

        String qtyUOM =
            eventJson
                .getAsJsonArray(INVENTORY_UPDATED_ITEMS)
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject(INVENTORY_QUANTITY_CHANGE_AND_ITEM_STATE)
                .getAsJsonObject(INVENTORY_QUANTITY_CHANGE)
                .get(INVENTORY_QTYUOM)
                .getAsString();

        int reasonCode =
            eventJson
                .getAsJsonObject(INVENTORY_ADJUSTMENT_REASON_CODE)
                .get(MOVE_TYPE_CODE)
                .getAsInt();
        JsonObject adjustmentTO = new JsonObject();
        adjustmentTO.addProperty(ReceivingConstants.INVENTORY_ADJUSTMENT_QTY, deltaQty);
        adjustmentTO.addProperty(ReceivingConstants.INVENTORY_ADJUSTMENT_QTY_UOM, qtyUOM);
        adjustmentTO.addProperty(ReceivingConstants.INVENTORY_ADJUSTMENT_REASON_CODE, reasonCode);
        switch (reasonCode) {
          case VDM_REASON:
            LOGGER.info("Processing VDM message received with trackingId={}", trackingId);
            containerService.processVendorDamageAdjustment(trackingId, adjustmentTO, headers);
            LOGGER.info("Successfully processed VDM message for trackingId={}", trackingId);
            break;
          case RCS_CONCEALED_SHORTAGE_REASON:
          case RCO_CONCEALED_OVERAGE_REASON:
            LOGGER.info(
                "Processing CONCEALED SHORTAGE OR OVERAGE message received with trackingId={}",
                trackingId);
            containerService.processConcealedShortageOrOverageAdjustment(
                trackingId, adjustmentTO, headers);
            LOGGER.info(
                "Successfully processed CONCEALED SHORTAGE OR OVERAGE message for trackingId={}",
                trackingId);
            break;
          case DAMAGE_REASON_CODE:
          case CONCEALED_DAMAGE_REASON_CODE:
            LOGGER.info(
                "Processing Damage message received with trackingId={}, damageQty={}",
                trackingId,
                deltaQty);
            containerService.processDamageAdjustment(
                trackingId, Integer.parseInt(deltaQty), headers);
            LOGGER.info(
                "Successfully processed Damage message received for trackingId={}, damageQty={}",
                trackingId,
                deltaQty);
            break;
          case INVENTORY_RECEIVING_CORRECTION_REASON_CODE:
            LOGGER.info(
                "Processing ReceivingCorrection for trackingId={}, reasonCode={}",
                trackingId,
                reasonCode);
            final String availableToSellQtyUOM =
                payloadJson
                    .getAsJsonArray(INVENTORY_LINES)
                    .get(0)
                    .getAsJsonObject()
                    .get(INVENTORY_QTYUOM)
                    .getAsString();
            final int vnpkRatio =
                payloadJson
                    .getAsJsonArray(INVENTORY_LINES)
                    .get(0)
                    .getAsJsonObject()
                    .get(INVENTORY_VNPK_RATIO)
                    .getAsInt();

            processReceivingCorrection(
                trackingId,
                adjustmentTO,
                headers,
                availableToSellQty,
                availableToSellQtyUOM,
                vnpkRatio);

            LOGGER.info(
                "Successfully processed ReceivingCorrection for trackingId={}, reasonCode={}",
                trackingId,
                reasonCode);
            break;
          case VTR_REASON_CODE:
            LOGGER.info(
                "Processing VTR for trackingId={}, reasonCode={}, quantity={} {}, availableToSellQty={}",
                trackingId,
                reasonCode,
                deltaQty,
                qtyUOM,
                availableToSellQty);
            witronContainerService.processVTR(
                trackingId, headers, Integer.parseInt(deltaQty), qtyUOM, availableToSellQty);
            LOGGER.info(
                "Successfully processed VTR for trackingId={}, reasonCode={}, quantity={} {}, availableToSellQty={}",
                trackingId,
                reasonCode,
                deltaQty,
                qtyUOM,
                availableToSellQty);
            break;
          default:
            LOGGER.info(
                "Ignoring inventory adjustment because invalid trackingId={}, reasonCode={}",
                trackingId,
                reasonCode);
        }
      } else if (headers.get(FLOW_NAME).contains(SPLIT_PALLET_TRANSFER)) {

        String newContainerTrackingId =
            eventJson
                .getAsJsonObject(INVENTORY_TARGET_CONTAINER)
                .get(INVENTORY_ADJUSTMENT_TRACKING_ID)
                .getAsString();
        String newContainerType = payloadJson.get(INVENTORY_CONTAINER_TYPE).getAsString();
        LOGGER.info(
            "processing split pallet for trackingId={}, newContainerTrackingId={}",
            trackingId,
            newContainerTrackingId);
        witronSplitPalletService.splitPallet(
            trackingId,
            availableToSellQty,
            newContainerTrackingId,
            newContainerType,
            Integer.parseInt(deltaQty),
            headers);
        LOGGER.info(
            "Successfully processed split pallet for trackingId={}, newContainerTrackingId={}",
            trackingId,
            newContainerTrackingId);
      }
    } catch (Exception e) {
      LOGGER.error(
          "Unable to process Kafka inventory adjustment message={}, Exception={}",
          messageData.toString(),
          ExceptionUtils.getStackTrace(e));
    }
  }

  private void clear() {
    TenantContext.clear();
  }

  private boolean isValidInventoryEventMessageForReceiving(JsonObject inventoryAdjustment_json) {
    return inventoryAdjustment_json.has(INVENTORY_ADJUSTMENT_EVENT_DATA)
        && inventoryAdjustment_json
            .getAsJsonObject(INVENTORY_ADJUSTMENT_EVENT_DATA)
            .has(INVENTORY_ADJUSTMENT_EVENT)
        && inventoryAdjustment_json.has(INVENTORY_PAYLOAD);
  }

  private boolean isValidInventoryEventForReceiving(HttpHeaders headers) {
    return headers
            .get(EVENT_TYPE)
            .get(0)
            .toString()
            .equalsIgnoreCase(INVENTORY_ADJUSTMENT_EVENT_CONTAINER_UPDATED)
        && (headers.get(FLOW_NAME).get(0).toString().equalsIgnoreCase(ADJUSTMENT_FLOW)
            || headers.get(FLOW_NAME).get(0).toString().equalsIgnoreCase(SPLIT_PALLET_TRANSFER));
  }

  public void processReceivingCorrection(
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
          "Ignoring inventory adjustment because `receiving' already processed ReceivingCorrection trackingId={}",
          trackingId);
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
