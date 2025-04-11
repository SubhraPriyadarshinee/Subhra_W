package com.walmart.move.nim.receiving.wfs.service;

import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.InventoryAdjustmentTO;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

public class WFSInventoryEventProcessor implements EventProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(WFSInventoryEventProcessor.class);

  @Autowired private WFSContainerService wfsContainerService;

  @Override
  public void processEvent(MessageData messageData) throws ReceivingException {
    InventoryAdjustmentTO inventoryAdjustmentTO = (InventoryAdjustmentTO) messageData;
    LOGGER.info(
        "Received inventory adjustment for WFS with text message:{}",
        inventoryAdjustmentTO.getJsonObject().toString().replace("\n", " "));

    if (!isValidInventoryAdjustment(inventoryAdjustmentTO.getJsonObject())) {
      LOGGER.info("Ignoring inventory adjustment for WFS because invalid eventObject");
      TenantContext.clear();
      return;
    }

    JsonObject eventObject =
        inventoryAdjustmentTO
            .getJsonObject()
            .getAsJsonObject(ReceivingConstants.INVENTORY_ADJUSTMENT_EVENT_OBJECT);

    if (eventObject.has(ReceivingConstants.INVENTORY_ADJUSTMENT_ITEM_LIST)
        && eventObject.has(ReceivingConstants.INVENTORY_ADJUSTMENT_TRACKING_ID)) {
      JsonObject item =
          (JsonObject)
              eventObject.getAsJsonArray(ReceivingConstants.INVENTORY_ADJUSTMENT_ITEM_LIST).get(0);
      String trackingId =
          eventObject.get(ReceivingConstants.INVENTORY_ADJUSTMENT_TRACKING_ID).getAsString();

      if (item.has(ReceivingConstants.INVENTORY_ADJUSTMENT_ADJUSTMENT_TO)) {
        JsonObject adjustmentTO =
            item.getAsJsonObject(ReceivingConstants.INVENTORY_ADJUSTMENT_ADJUSTMENT_TO);

        // Setting headers
        HttpHeaders headers = inventoryAdjustmentTO.getHttpHeaders();

        if (adjustmentTO.has(ReceivingConstants.INVENTORY_ADJUSTMENT_REASON_CODE)) {
          String reasonCode =
              adjustmentTO.get(ReceivingConstants.INVENTORY_ADJUSTMENT_REASON_CODE).toString();
          if (Integer.parseInt(reasonCode) == ReceivingConstants.VTR_REASON_CODE) {
            LOGGER.info("Valid VTR message received for WFS with trackingId:{}", trackingId);
            wfsContainerService.backoutContainerForWFS(trackingId, headers);
          } else {
            LOGGER.info("Ignoring inventory adjustment for WFS because invalid reasonCode");
          }
        }
      }
    }
  }

  private boolean isValidInventoryAdjustment(JsonObject inventoryAdjustment) {
    return inventoryAdjustment.has(ReceivingConstants.INVENTORY_ADJUSTMENT_EVENT)
        && inventoryAdjustment
            .get(ReceivingConstants.INVENTORY_ADJUSTMENT_EVENT)
            .getAsString()
            .equalsIgnoreCase(ReceivingConstants.INVENTORY_ADJUSTMENT_EVENT_CONTAINER_UPDATED)
        && inventoryAdjustment.has(ReceivingConstants.INVENTORY_ADJUSTMENT_EVENT_OBJECT);
  }
}
