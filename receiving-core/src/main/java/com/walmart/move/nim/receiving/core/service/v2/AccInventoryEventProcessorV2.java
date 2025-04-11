package com.walmart.move.nim.receiving.core.service.v2;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.InventoryAdjustmentTO;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service(ReceivingConstants.INVENTORY_ADJUSTMENT_PROCESSOR_V2)
public class AccInventoryEventProcessorV2 implements EventProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(AccInventoryEventProcessorV2.class);

  @Autowired private ContainerService containerService;

  @Override
  public void processEvent(MessageData messageData) throws ReceivingException {
    InventoryAdjustmentTO inventoryAdjustmentTO = (InventoryAdjustmentTO) messageData;
    LOGGER.info(
        "Received inventory adjustment v2 with text message:{}",
        inventoryAdjustmentTO.getJsonObject());

    if (!isValidInventoryAdjustment(inventoryAdjustmentTO.getJsonObject())) {
      LOGGER.info("Ignoring inventory adjustment v2 because invalid eventObject");
      TenantContext.clear();
      return;
    }
    JsonObject eventData =
        inventoryAdjustmentTO
            .getJsonObject()
            .getAsJsonObject(ReceivingConstants.INVENTORY_ADJUSTMENT_EVENT_DATA);
    LOGGER.info("Received inventory adjustment v2 with eventDataObject:{}", eventData);

    if (eventData.has(ReceivingConstants.EVENT)) {
      JsonObject event = eventData.getAsJsonObject(ReceivingConstants.EVENT);
      LOGGER.info("Received inventory adjustment v2 with event:{}", event);
      processEventObject(event, inventoryAdjustmentTO);
    }
  }

  private void processEventObject(JsonObject event, InventoryAdjustmentTO inventoryAdjustmentTO)
      throws ReceivingException {
    if (event.has(ReceivingConstants.INVENTORY_ADJUSTMENT_REASON_CODE)
        && event
            .getAsJsonObject(ReceivingConstants.INVENTORY_ADJUSTMENT_REASON_CODE)
            .has(ReceivingConstants.INVENTORY_ADJUSTMENT_CODE)) {
      JsonObject updatedItems =
          (JsonObject) event.getAsJsonArray(ReceivingConstants.INVENTORY_UPDATED_ITEMS).get(0);
      LOGGER.info("Received inventory adjustment v2 with updatedItems:{}", updatedItems);
      JsonObject adjustment = new JsonObject();
      if (updatedItems.has(ReceivingConstants.ITEM_CHANGE_LIST)) {
        Integer deltaQtySum = 0;
        JsonArray itemChangeList = updatedItems.getAsJsonArray(ReceivingConstants.ITEM_CHANGE_LIST);
        for (JsonElement i : itemChangeList) {
          JsonObject quantityChange =
              i.getAsJsonObject().getAsJsonObject(ReceivingConstants.INVENTORY_QUANTITY_CHANGE);
          LOGGER.info("Received inventory adjustment v2 with quantityChange:{}", quantityChange);
          if (quantityChange.has(ReceivingConstants.INVENTORY_DELTAQTY)
              && quantityChange.has(ReceivingConstants.INVENTORY_QTYUOM)) {
            deltaQtySum =
                deltaQtySum
                    + (quantityChange.get(ReceivingConstants.INVENTORY_DELTAQTY).getAsInt());
            adjustment.addProperty(ReceivingConstants.INVENTORY_ADJUSTMENT_QTY, deltaQtySum);
            adjustment.addProperty(
                ReceivingConstants.INVENTORY_ADJUSTMENT_QTY_UOM,
                quantityChange.get(ReceivingConstants.INVENTORY_QTYUOM).getAsString());
            LOGGER.info("Received inventory adjustment v2 with adjustment:{}", adjustment);
          }
        }
      }
      processReasonCode(event, inventoryAdjustmentTO, adjustment);
    }
  }

  private void processReasonCode(
      JsonObject event, InventoryAdjustmentTO inventoryAdjustmentTO, JsonObject adjustment)
      throws ReceivingException {
    if (event.has(ReceivingConstants.INVENTORY_ADJUSTMENT_REASON_CODE)) {
      JsonObject reasonCodeObject =
          event.getAsJsonObject(ReceivingConstants.INVENTORY_ADJUSTMENT_REASON_CODE);
      // Setting headers
      HttpHeaders headers = inventoryAdjustmentTO.getHttpHeaders();
      String trackingId =
          event.get(ReceivingConstants.INVENTORY_ADJUSTMENT_TRACKING_ID).getAsString();

      if (reasonCodeObject.has(ReceivingConstants.INVENTORY_ADJUSTMENT_CODE)) {
        String reasonCode =
            reasonCodeObject.get(ReceivingConstants.INVENTORY_ADJUSTMENT_CODE).toString();
        adjustment.addProperty(ReceivingConstants.INVENTORY_ADJUSTMENT_REASON_CODE, reasonCode);
        if (Integer.parseInt(reasonCode) == ReceivingConstants.VTR_REASON_CODE) {
          LOGGER.info(
              "Valid VTR message received with trackingId:{} headers:{}", trackingId, headers);
          containerService.backoutContainer(trackingId, headers);
        } else if (Integer.parseInt(reasonCode) == ReceivingConstants.DAMAGE_REASON_CODE) {
          String damageQty = adjustment.get(ReceivingConstants.INVENTORY_ADJUSTMENT_QTY).toString();
          LOGGER.info(
              "Valid Damage message received with trackingId:{}, damageQty:{} headers:{}",
              trackingId,
              damageQty,
              headers);
          containerService.processDamageAdjustment(
              trackingId, Integer.parseInt(damageQty), headers);
        } else if (Integer.parseInt(reasonCode) == ReceivingConstants.VDM_REASON_CODE) {
          LOGGER.info(
              "Valid VDM message received with trackingId:{} adjustment:{} headers:{}",
              trackingId,
              adjustment,
              headers);
          containerService.processVendorDamageAdjustment(trackingId, adjustment, headers);
        } else if (Integer.parseInt(reasonCode)
                == ReceivingConstants.RCS_CONCEALED_SHORTAGE_REASON_CODE
            || Integer.parseInt(reasonCode)
                == ReceivingConstants.RCO_CONCEALED_OVERAGE_REASON_CODE) {
          LOGGER.info(
              "Valid CONCEALED SHORTAGE OR OVERAGE message received with trackingId:{}  adjustment:{} headers:{}",
              trackingId,
              adjustment,
              headers);
          containerService.processConcealedShortageOrOverageAdjustment(
              trackingId, adjustment, headers);
        } else {
          LOGGER.info("Ignoring inventory adjustment because invalid reasonCode");
        }
      }
    }
  }

  private boolean isValidInventoryAdjustment(JsonObject inventoryAdjustment) {
    return inventoryAdjustment.has(ReceivingConstants.INVENTORY_ADJUSTMENT_EVENT_DATA)
        && inventoryAdjustment
            .getAsJsonObject(ReceivingConstants.INVENTORY_ADJUSTMENT_EVENT_DATA)
            .has(ReceivingConstants.EVENT)
        && inventoryAdjustment
            .getAsJsonObject(ReceivingConstants.INVENTORY_ADJUSTMENT_EVENT_DATA)
            .getAsJsonObject(ReceivingConstants.EVENT)
            .get(ReceivingConstants.SYM_EVENT_TYPE_KEY)
            .getAsString()
            .equalsIgnoreCase(ReceivingConstants.INVENTORY_ADJUSTMENT_EVENT_CONTAINER_UPDATED);
  }
}
