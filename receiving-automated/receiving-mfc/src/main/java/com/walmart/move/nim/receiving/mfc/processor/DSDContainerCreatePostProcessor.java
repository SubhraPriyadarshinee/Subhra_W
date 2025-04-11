package com.walmart.move.nim.receiving.mfc.processor;

import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.MARKET_FULFILLMENT_CENTER;
import static com.walmart.move.nim.receiving.mfc.model.common.QuantityType.NGR_REJECT;
import static com.walmart.move.nim.receiving.mfc.model.common.QuantityType.NGR_SHORTAGE;
import static com.walmart.move.nim.receiving.mfc.model.gdm.PackType.MIXED_PACK;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.framework.message.processor.ProcessExecutor;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.model.InventoryItemExceptionPayload;
import com.walmart.move.nim.receiving.core.model.ngr.InventoryDetail;
import com.walmart.move.nim.receiving.core.model.ngr.NGRPack;
import com.walmart.move.nim.receiving.core.model.ngr.PackItem;
import com.walmart.move.nim.receiving.core.service.InventoryService;
import com.walmart.move.nim.receiving.mfc.common.MFCConstant;
import com.walmart.move.nim.receiving.mfc.config.MFCManagedConfig;
import com.walmart.move.nim.receiving.mfc.model.common.QuantityType;
import com.walmart.move.nim.receiving.mfc.service.MixedPalletRejectService;
import com.walmart.move.nim.receiving.mfc.transformer.NGRAsnTransformer;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class DSDContainerCreatePostProcessor implements ProcessExecutor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DSDContainerCreatePostProcessor.class);

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Autowired private ProcessInitiator processInitiator;

  @Autowired private NGRAsnTransformer ngrAsnTransformer;

  @Autowired private MixedPalletRejectService mixedPalletRejectService;

  @Autowired private InventoryService inventoryService;

  @ManagedConfiguration private MFCManagedConfig mfcManagedConfig;

  @Override
  public void doExecute(ReceivingEvent receivingEvent) {
    NGRPack ngrPack = JacksonParser.convertJsonToObject(receivingEvent.getPayload(), NGRPack.class);
    Long deliveryNumber = Long.valueOf(receivingEvent.getKey());
    createDeliveryUnloadCompleteEvent(deliveryNumber);
    performAuditHandling(ngrPack, deliveryNumber);
    performMixedPalletReject(ngrPack, Long.valueOf(receivingEvent.getKey()));
  }

  /**
   * This method is used for sending inventory adjustment for NGR_Reject and NGR_Shortage.
   *
   * @param ngrPack
   * @param deliveryNumber
   */
  private void performAuditHandling(NGRPack ngrPack, Long deliveryNumber) {
    LOGGER.info("Start Performing audit handling for deliveryNumber {}", deliveryNumber);

    List<InventoryItemExceptionPayload> inventoryItemExceptionPayloadList = new ArrayList<>();
    for (PackItem packItem : ngrPack.getItems()) {
      InventoryDetail inventoryDetail = packItem.getInventoryDetail();
      int receivedQty =
          Objects.nonNull(inventoryDetail.getReceivedQuantity())
              ? (int) Double.parseDouble(inventoryDetail.getReceivedQuantity())
              : 0;
      int rejectedQty =
          Objects.nonNull(inventoryDetail.getRejectedQuantity())
              ? (int) Double.parseDouble(inventoryDetail.getRejectedQuantity())
              : 0;
      int exceptedQty =
          Objects.nonNull(inventoryDetail.getExpectedQuantity())
              ? (int) Double.parseDouble(inventoryDetail.getExpectedQuantity())
              : 0;
      int currentQty = exceptedQty;
      int shortageQty = exceptedQty - rejectedQty - receivedQty;
      if (rejectedQty > 0) {
        InventoryItemExceptionPayload inventoryItemExceptionPayload =
            createInventoryItemExceptionPayloadObject(
                ngrPack.getPackNumber(),
                packItem.getGtin(),
                packItem.getItemNumber(),
                currentQty,
                packItem.getInventoryDetail(),
                rejectedQty,
                NGR_REJECT);
        inventoryItemExceptionPayloadList.add(inventoryItemExceptionPayload);
        currentQty = currentQty - rejectedQty;
      }
      if (shortageQty > 0) {
        InventoryItemExceptionPayload inventoryItemExceptionPayload =
            createInventoryItemExceptionPayloadObject(
                ngrPack.getPackNumber(),
                packItem.getGtin(),
                packItem.getItemNumber(),
                currentQty,
                packItem.getInventoryDetail(),
                shortageQty,
                NGR_SHORTAGE);
        inventoryItemExceptionPayloadList.add(inventoryItemExceptionPayload);
      }
    }
    if (CollectionUtils.isNotEmpty(inventoryItemExceptionPayloadList)) {
      String responsePayload =
          inventoryService.performInventoryBulkAdjustmentForItems(
              inventoryItemExceptionPayloadList);
      LOGGER.info("Adjusted qty from inventory . Response = {}", responsePayload);
    }
    LOGGER.info(
        "Performed audit handling for deliveryNumber {} and count {}",
        deliveryNumber,
        inventoryItemExceptionPayloadList.size());
  }

  private InventoryItemExceptionPayload createInventoryItemExceptionPayloadObject(
      String trackingId,
      String gtinNumber,
      String itemNumber,
      int currentQty,
      InventoryDetail inventoryDetail,
      int adjustByQty,
      QuantityType quantityType) {
    return InventoryItemExceptionPayload.builder()
        .trackingId(trackingId)
        .itemNumber(Long.valueOf(itemNumber))
        .baseDivisionCode(BASE_DIVISION_CODE)
        .financialReportingGroup(FINANCIAL_REPORTING_GROUP_CODE)
        .currentQty(currentQty)
        .currentQuantityUOM(inventoryDetail.getExpectedQuantityUom())
        .adjustBy(-1 * adjustByQty)
        .adjustedQuantityUOM(inventoryDetail.getExpectedQuantityUom())
        .itemUpc(gtinNumber)
        .reasonCode(quantityType.getInventoryErrorReason())
        .reasonDesc(quantityType.getType())
        .createUserid(TenantContext.getUserId())
        .orgUnitId(String.valueOf(TenantContext.getOrgUnitId()))
        .build();
  }

  private void createDeliveryUnloadCompleteEvent(Long delivery) {
    LOGGER.info("Creating event for Unload Complete for delivery number {}", delivery);
    Map<String, Object> additionalAttribute = new HashMap<>();
    additionalAttribute.put(
        MFCConstant.EVENT_RUN_AFTER_THRESHOLD_TIME_MINUTES,
        mfcManagedConfig.getDsdDeliveryUnloadCompleteEventThresholdMinutes());
    additionalAttribute.put(
        ReceivingConstants.ACTION_TYPE, ReceivingConstants.CREATE_DELIVERY_UNLOAD_COMPLETE_EVENT);
    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(JacksonParser.writeValueAsString(delivery))
            .processor(DELIVERY_UNLOAD_COMPLETE_CREATE_EVENT_PROCESSOR)
            .additionalAttributes(additionalAttribute)
            .build();
    processInitiator.initiateProcess(receivingEvent, null);
  }

  private void performMixedPalletReject(NGRPack ngrPack, Long deliveryNumber) {
    if (Objects.nonNull(ngrPack)
        && io.strati.libs.commons.collections.CollectionUtils.isNotEmpty(ngrPack.getItems())) {
      Set<String> replenishmentCodes =
          ngrPack
              .getItems()
              .stream()
              .map(PackItem::getReplenishmentCode)
              .collect(Collectors.toSet());
      if (io.strati.libs.commons.collections.CollectionUtils.isNotEmpty(replenishmentCodes)
          && replenishmentCodes.size() > 1
          && replenishmentCodes.contains(MARKET_FULFILLMENT_CENTER)) {
        LOGGER.info("Mixed items exists for pack number {}", ngrPack.getPackNumber());
        ngrPack.setReceivingDeliveryId(String.valueOf(deliveryNumber));
        mixedPalletRejectService.processMixedPalletReject(
            ngrAsnTransformer.apply(ngrPack, MIXED_PACK.getPackType()), deliveryNumber);
      }
    }
  }

  @Override
  public boolean isAsync() {
    return tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        MFCConstant.DSD_CONTAINER_CREATE_POST_PROCESSOR_IN_ASYNC_MODE,
        Boolean.TRUE);
  }
}
