package com.walmart.move.nim.receiving.mfc.processor;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ENABLE_ON_SCAN_RECEIPT;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.InventoryAdjustmentTO;
import com.walmart.move.nim.receiving.mfc.common.PalletType;
import com.walmart.move.nim.receiving.mfc.model.common.AuditStatus;
import com.walmart.move.nim.receiving.mfc.model.common.CommonReceiptDTO;
import com.walmart.move.nim.receiving.mfc.model.common.QuantityType;
import com.walmart.move.nim.receiving.mfc.model.inventory.ItemListItem;
import com.walmart.move.nim.receiving.mfc.model.inventory.MFCInventoryAdjustmentDTO;
import com.walmart.move.nim.receiving.mfc.service.DecantAuditService;
import com.walmart.move.nim.receiving.mfc.service.MFCContainerService;
import com.walmart.move.nim.receiving.mfc.service.MFCReceivingService;
import com.walmart.move.nim.receiving.mfc.transformer.InventoryReceiptTransformer;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class MFCInventoryAdjustmentProcessor implements EventProcessor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MFCInventoryAdjustmentProcessor.class);

  @Autowired private InventoryReceiptTransformer inventoryReceiptTransformer;

  @Autowired private MFCReceivingService mfcReceivingService;

  @Autowired private MFCContainerService mfcContainerService;

  @Autowired private DecantAuditService decantAuditService;

  @Autowired private Gson gson;
  private static final List<String> DISALLOWED_INVENTORY_EVENTS =
      Arrays.asList("container.deleted");

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Override
  public void processEvent(MessageData adjustmentData) throws ReceivingException {
    LOGGER.info("Entering from InventoryAdjustment Processor");

    if (!(adjustmentData instanceof InventoryAdjustmentTO)) {
      LOGGER.error(
          "Not a type of Inventory Adjustment. Hence, not processing. payload = {}",
          adjustmentData);
    }

    InventoryAdjustmentTO inventoryAdjustmentTO = (InventoryAdjustmentTO) adjustmentData;
    LOGGER.info("Got Inventory Adjustment . Payload = {}", inventoryAdjustmentTO);

    MFCInventoryAdjustmentDTO mfcInventoryAdjustmentDTO =
        gson.fromJson(
            gson.toJson(inventoryAdjustmentTO.getJsonObject()), MFCInventoryAdjustmentDTO.class);

    if (Objects.isNull(TenantContext.getMessageIdempotencyId())) {
      LOGGER.info("Message Idempotency Key is null and hence, ignoring the message");
      return;
    }

    if (decantAuditService
        .findByCorrelationId(TenantContext.getMessageIdempotencyId())
        .isPresent()) {
      LOGGER.info(
          "Duplicate inventory Adjustment Detected . Hence Ignoring it . IdempotencyKey = {} , payload = {}",
          TenantContext.getMessageIdempotencyId(),
          adjustmentData);
      return;
    }

    if (DISALLOWED_INVENTORY_EVENTS.contains(mfcInventoryAdjustmentDTO.getEvent())) {
      LOGGER.warn(
          "Inventory EventType = {} is not allowed and hence, ignoring events ",
          mfcInventoryAdjustmentDTO.getEvent());
      return;
    }

    // Ignore store pallet stocking
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(ENABLE_ON_SCAN_RECEIPT)
        && PalletType.STORE.equalsType(mfcInventoryAdjustmentDTO.getEventObject().getLocationName())
        && !eventProcessableForStoreAddSKU(mfcInventoryAdjustmentDTO)) {
      LOGGER.warn(
          "Store stocking operation and hence, inventory adjustment is getting ignored. palletId={}",
          mfcInventoryAdjustmentDTO.getEventObject().getTrackingId());
      return;
    }

    AuditStatus auditStatus = AuditStatus.SUCCESS;
    List<Receipt> receiptList = new ArrayList<>();
    try {

      List<Container> containers =
          mfcContainerService.findContainerBySSCC(
              mfcInventoryAdjustmentDTO.getEventObject().getTrackingId());

      // Slot Container should not processed
      if (Objects.isNull(containers) || containers.isEmpty()) {
        LOGGER.info(
            "Invalid container for receiving to proceed forward and hence skipping it. trackingId = {}",
            mfcInventoryAdjustmentDTO.getEventObject().getTrackingId());
        return;
      }

      if (mfcContainerService.addSkuIfRequired(mfcInventoryAdjustmentDTO)) {
        receiptList = null;
      } else {
        CommonReceiptDTO receiptDTO =
            inventoryReceiptTransformer.transform(mfcInventoryAdjustmentDTO);
        receiptList = mfcReceivingService.performReceiving(receiptDTO);
      }
    } catch (Exception exception) {
      processExceptionLogging(exception, inventoryAdjustmentTO);
      auditStatus = AuditStatus.FAILURE;
    } finally {
      decantAuditService.createInventoryAdjustmentAudit(
          mfcInventoryAdjustmentDTO, inventoryAdjustmentTO, auditStatus, receiptList);
    }

    LOGGER.info("Exiting from InventoryAdjustment Processor");
  }

  private boolean eventProcessableForStoreAddSKU(
      MFCInventoryAdjustmentDTO mfcInventoryAdjustmentDTO) {

    Optional<ItemListItem> _itemListItem =
        mfcInventoryAdjustmentDTO
            .getEventObject()
            .getItemList()
            .stream()
            .filter(item -> Objects.nonNull(item.getAdjustmentTO()))
            .findFirst();

    if (!_itemListItem.isPresent()) {
      return Boolean.FALSE;
    }

    ItemListItem itemListItem = _itemListItem.get();

    if (Objects.nonNull(itemListItem.getAdjustmentTO())
        && Objects.nonNull(itemListItem.getAdjustmentTO().getReasonCode())
        && (itemListItem.getAdjustmentTO().getReasonCode().intValue()
            != QuantityType.OVERAGE.getInventoryErrorReason().intValue())) {
      LOGGER.info("Store : Error Reason code is not overage and hence, skipping addSku workflow ");
      return Boolean.FALSE;
    }
    return Boolean.TRUE;
  }

  private void processExceptionLogging(
      Exception exception, InventoryAdjustmentTO inventoryAdjustmentTO) {
    if (exception instanceof ReceivingInternalException) {
      ReceivingInternalException receivingInternalException =
          (ReceivingInternalException) exception;
      if (receivingInternalException
          .getErrorCode()
          .equalsIgnoreCase(ExceptionCodes.CONTAINER_NOT_FOUND)) {
        LOGGER.error(
            "No Container found and hence, ignoring it . InventoryPayload = {} ",
            ReceivingUtils.stringfyJson(inventoryAdjustmentTO),
            receivingInternalException);
        return;
      }
      LOGGER.error(
          "Unable to process inventory adjustment.InventoryPayload = {} ",
          ReceivingUtils.stringfyJson(inventoryAdjustmentTO),
          receivingInternalException);
      return;
    }
    LOGGER.error(
        "Exception occurred while processing inventoryAdjustment . Payload = {} , exception = ",
        inventoryAdjustmentTO,
        exception);
  }
}
