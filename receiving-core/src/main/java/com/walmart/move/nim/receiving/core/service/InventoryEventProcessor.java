package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DAMAGE_REASON_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DEFAULT_INVENTORY_ADJUSTMENT_PROCESSOR;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.RCO_CONCEALED_OVERAGE_REASON_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.RCS_CONCEALED_SHORTAGE_REASON_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.SAMS_BASE_DIVISION_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.SAMS_VENDOR_CONCEALED_DAMAGE_OR_SHORT_REASON_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.SAMS_VENDOR_CONCEALED_OVERAGE_REASON_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.VDM_REASON_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.VTR_REASON_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.VTX_REASON_CODE;

import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.message.listener.InventoryAdjustmentListener;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.InventoryAdjustmentTO;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.libs.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service(DEFAULT_INVENTORY_ADJUSTMENT_PROCESSOR)
public class InventoryEventProcessor implements EventProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(InventoryAdjustmentListener.class);

  @Autowired private ContainerService containerService;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Override
  public void processEvent(MessageData messageData) throws ReceivingException {
    InventoryAdjustmentTO inventoryAdjustmentTO = (InventoryAdjustmentTO) messageData;
    LOGGER.info(
        "Received inventory adjustment with text message:{}",
        inventoryAdjustmentTO.getJsonObject().toString().replace("\n", " "));

    if (!isValidInventoryAdjustment(inventoryAdjustmentTO.getJsonObject())) {
      LOGGER.info("Ignoring inventory adjustment because invalid eventObject");
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
      String containerType = null;
      if (eventObject.has(ReceivingConstants.INVENTORY_ADJUSTMENT_CONTAINER_TYPE)) {
        containerType =
            eventObject.get(ReceivingConstants.INVENTORY_ADJUSTMENT_CONTAINER_TYPE).getAsString();
      }

      if (item.has(ReceivingConstants.INVENTORY_ADJUSTMENT_ADJUSTMENT_TO)) {
        JsonObject adjustmentTO =
            item.getAsJsonObject(ReceivingConstants.INVENTORY_ADJUSTMENT_ADJUSTMENT_TO);

        HttpHeaders headers = inventoryAdjustmentTO.getHttpHeaders();

        if (adjustmentTO.has(ReceivingConstants.INVENTORY_ADJUSTMENT_REASON_CODE)) {
          containerAdjustment(item, adjustmentTO, eventObject, containerType, trackingId, headers);
        }
      }
    }
  }

  private void containerAdjustment(
      JsonObject item,
      JsonObject adjustmentTO,
      JsonObject eventObject,
      String containerType,
      String trackingId,
      HttpHeaders headers)
      throws ReceivingException {

    String baseDivisionCode = ReceivingConstants.WM_BASE_DIVISION_CODE;
    if (item.has(ReceivingConstants.INVENTORY_BASE_DIVISION_CODE)) {
      baseDivisionCode = item.get(ReceivingConstants.INVENTORY_BASE_DIVISION_CODE).getAsString();
    }
    Integer reasonCode =
        Integer.parseInt(
            adjustmentTO.get(ReceivingConstants.INVENTORY_ADJUSTMENT_REASON_CODE).toString());

    switch (reasonCode) {
      case VTR_REASON_CODE:
      case VTX_REASON_CODE:
        LOGGER.info(
            "Valid VTR/VTX message received with trackingId:{} for reason code : {}",
            trackingId,
            reasonCode);
        containerService.backoutContainer(trackingId, headers);
        break;

      case DAMAGE_REASON_CODE:
        String damageQty = adjustmentTO.get(ReceivingConstants.INVENTORY_ADJUSTMENT_QTY).toString();
        LOGGER.info(
            "Valid Damage message received with trackingId:{}, damageQty:{}",
            trackingId,
            damageQty);
        containerService.processDamageAdjustment(trackingId, Integer.parseInt(damageQty), headers);
        break;

      case VDM_REASON_CODE:
        if (isValidInventoryRIPAdjustment(item, containerType, reasonCode)) {
          inventoryRIPAdjustment(item, adjustmentTO, eventObject, reasonCode, trackingId, headers);
        } else {
          LOGGER.info("Valid VDM message received with trackingId:{}", trackingId);
          containerService.processVendorDamageAdjustment(trackingId, adjustmentTO, headers);
        }
        break;

      case RCS_CONCEALED_SHORTAGE_REASON_CODE:
        if (isValidInventoryRIPAdjustment(item, containerType, reasonCode)) {
          inventoryRIPAdjustment(item, adjustmentTO, eventObject, reasonCode, trackingId, headers);
        } else {
          LOGGER.info(
              "Valid CONCEALED SHORTAGE OR OVERAGE message received with trackingId:{}",
              trackingId);
          containerService.processConcealedShortageOrOverageAdjustment(
              trackingId, adjustmentTO, headers);
        }
        break;
      case RCO_CONCEALED_OVERAGE_REASON_CODE:
        LOGGER.info(
            "Valid CONCEALED SHORTAGE OR OVERAGE message received with trackingId:{}", trackingId);
        containerService.processConcealedShortageOrOverageAdjustment(
            trackingId, adjustmentTO, headers);
        break;

      case SAMS_VENDOR_CONCEALED_DAMAGE_OR_SHORT_REASON_CODE:
      case SAMS_VENDOR_CONCEALED_OVERAGE_REASON_CODE:
        if (SAMS_BASE_DIVISION_CODE.equalsIgnoreCase(baseDivisionCode)) {
          LOGGER.info(
              "Valid SAMS Overage Shortage Damage message received with trackingId:{}, reasonCode :{}",
              trackingId,
              reasonCode);
          containerService.processConcealedShortageOrOverageAdjustment(
              trackingId, adjustmentTO, headers);
        }
        break;
      default:
        LOGGER.info("Ignoring inventory adjustment because invalid reasonCode");
    }
  }

  private void inventoryRIPAdjustment(
      JsonObject item,
      JsonObject adjustmentTO,
      JsonObject eventObject,
      Integer reasonCode,
      String trackingId,
      HttpHeaders headers) {
    LOGGER.info("RIP Adjustment with trackingId:{}", trackingId);
    Long deliveryNumber = eventObject.get(ReceivingConstants.DELIVERY_NUMBER).getAsLong();
    containerService.processRIPNegativeDamagedAdjustment(
        trackingId,
        deliveryNumber,
        reasonCode,
        item,
        adjustmentTO,
        headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
  }

  /**
   * RIP damaged and negative damaged adjustment process
   *
   * @param item
   * @param containerType
   * @param reasonCode
   * @return
   */
  private boolean isValidInventoryRIPAdjustment(
      JsonObject item, String containerType, Integer reasonCode) {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            ReceivingConstants.ENABLE_RIP_INVENTORY_ADJUSTMENT)
        && (reasonCode.equals(VDM_REASON_CODE)
            || reasonCode.equals(RCS_CONCEALED_SHORTAGE_REASON_CODE))
        && item.has(ReceivingConstants.INVENTORY_ITEM_STATUS_CHANGE)) {
      JsonObject itemStatusChange =
          item.getAsJsonObject(ReceivingConstants.INVENTORY_ITEM_STATUS_CHANGE);
      if (itemStatusChange.has(ReceivingConstants.INVENTORY_RECEIVING_IN_PROGRESS_QTY)
          && StringUtils.isNotEmpty(containerType)
          && ReceivingConstants.INV_TOTE.equalsIgnoreCase(containerType)) {
        Integer ripNegativeQty =
            Integer.parseInt(
                itemStatusChange
                    .get(ReceivingConstants.INVENTORY_RECEIVING_IN_PROGRESS_QTY)
                    .toString());
        if (ripNegativeQty < 0) {
          return true;
        }
      }
    }
    return false;
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
