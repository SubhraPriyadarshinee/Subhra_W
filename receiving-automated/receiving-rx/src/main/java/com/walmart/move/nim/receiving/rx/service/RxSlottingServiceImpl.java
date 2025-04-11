package com.walmart.move.nim.receiving.rx.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.getForwardablHeaderWithTenantData;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityCountryCode;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ENABLE_OUTBOX_CREATE_MOVES;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.slotting.SlottingRestApiClient;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.OutboxConfig;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingContainerDetails;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingContainerItemDetails;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletRequest;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom;
import com.walmart.platform.messages.MetaData;
import com.walmart.platform.repositories.OutboxEvent;
import com.walmart.platform.service.OutboxEventSinkService;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.Counted;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.time.Instant;
import java.util.*;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Service
public class RxSlottingServiceImpl {

  private static final Logger LOGGER = LoggerFactory.getLogger(RxSlottingServiceImpl.class);

  @Autowired private SlottingRestApiClient slottingRestApiClient;
  @Resource private Gson gson;
  @Resource private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Resource private OutboxEventSinkService outboxEventSinkService;
  @ManagedConfiguration private OutboxConfig outboxConfig;

  @Counted(
      name = "rxAcquireSlotHitCount",
      level1 = "uwms-receiving",
      level2 = "rxSlottingServiceImpl",
      level3 = "rxAcquireSlot")
  @Timed(
      name = "acquireSlotTimes",
      level1 = "uwms-receiving",
      level2 = "rxSlottingServiceImpl",
      level3 = "acquireSlot")
  @ExceptionCounted(
      name = "acquireSlotExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rxSlottingServiceImpl",
      level3 = "acquireSlot")
  public SlottingPalletResponse acquireSlot(
      String messageId,
      List<Long> itemNumbers,
      int locationSize,
      String findSlotType,
      HttpHeaders httpHeaders) {
    return acquireSlot(
        messageId,
        itemNumbers,
        locationSize,
        findSlotType,
        null,
        null,
        null,
        null,
        null,
        null,
        httpHeaders);
  }

  @Counted(
      name = "rxAcquireSlotHitCount",
      level1 = "uwms-receiving",
      level2 = "rxSlottingServiceImpl",
      level3 = "rxAcquireSlot")
  @Timed(
      name = "acquireSlotTimes",
      level1 = "uwms-receiving",
      level2 = "rxSlottingServiceImpl",
      level3 = "acquireSlot")
  @ExceptionCounted(
      name = "acquireSlotExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rxSlottingServiceImpl",
      level3 = "acquireSlot")
  public SlottingPalletResponse acquireSlot(
      String messageId,
      List<Long> itemNumbers,
      int locationSize,
      String findSlotType,
      String newLabelTrackingId,
      Integer qty,
      String qtyUom,
      String gtin,
      String manualSlot,
      String fromLocation,
      HttpHeaders httpHeaders) {

    SlottingPalletRequest slottingRxPalletRequest = new SlottingPalletRequest();
    // existence of the slot header means call has come from outbox
    if (httpHeaders.containsKey(ReceivingConstants.MOVE_TO_LOCATION)) {
      slottingRxPalletRequest.setGenerateMove(true);
    }
    httpHeaders = ReceivingUtils.getForwardableHttpHeaders(httpHeaders);
    httpHeaders.set(ReceivingConstants.SLOTTING_FEATURE_TYPE, findSlotType);

    slottingRxPalletRequest.setMessageId(messageId);

    SlottingContainerDetails slottingRxContainerDetails = new SlottingContainerDetails();
    List<SlottingContainerDetails> slottingRxContainerDetailsList = new ArrayList<>();
    List<SlottingContainerItemDetails> slottingRxContainerItemDetailsList = new ArrayList<>();

    for (Long itemNumber : itemNumbers) {
      SlottingContainerItemDetails slottingRxContainerItemDetails =
          new SlottingContainerItemDetails();
      slottingRxContainerItemDetails.setItemNbr(itemNumber);
      if (StringUtils.isNotBlank(newLabelTrackingId)) {
        if (Objects.nonNull(qty)) slottingRxContainerItemDetails.setQty(qty);
        if (StringUtils.isNotBlank(qtyUom)) slottingRxContainerItemDetails.setQtyUom(qtyUom);
        if (StringUtils.isNotBlank(gtin)) slottingRxContainerItemDetails.setItemUPC(gtin);
      }
      slottingRxContainerItemDetailsList.add(slottingRxContainerItemDetails);
    }
    if (StringUtils.isNotBlank(newLabelTrackingId)) {
      slottingRxContainerDetails.setContainerTrackingId(newLabelTrackingId);
    }
    if (locationSize > 0) {
      slottingRxContainerDetails.setLocationSize(locationSize);
    }
    slottingRxContainerDetails.setFromLocation(fromLocation);
    slottingRxContainerDetails.setLocationName(manualSlot);
    slottingRxContainerDetails.setContainerItemsDetails(slottingRxContainerItemDetailsList);
    slottingRxContainerDetailsList.add(slottingRxContainerDetails);

    slottingRxPalletRequest.setContainerDetails(slottingRxContainerDetailsList);

    // these conditions mean moves should be created via outbox
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), ENABLE_OUTBOX_CREATE_MOVES, false)
        && slottingRxPalletRequest.isGenerateMove()) {
      Map<String, Object> headers = getForwardablHeaderWithTenantData(httpHeaders);
      headers.put(ReceivingConstants.SLOTTING_FEATURE_TYPE, findSlotType);
      headers.put(ReceivingConstants.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
      headers.put(ReceivingConstants.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
      MetaData metaData = MetaData.emptyInstance();
      String eventId =
          getFacilityCountryCode()
              + "_"
              + getFacilityNum()
              + "_"
              + newLabelTrackingId
              + "_"
              + fromLocation
              + "_"
              + manualSlot;
      OutboxEvent outboxEvent =
          RxUtils.buildOutboxEvent(
              headers,
              gson.toJson(slottingRxPalletRequest),
              eventId,
              metaData,
              outboxConfig.getOutboxPolicyHttpCreateMoves(),
              Instant.now());
      outboxEventSinkService.saveEvent(outboxEvent);
      LOGGER.info("Persisted in outbox eventId: " + eventId);
      return null;
    }

    return slottingRestApiClient.getSlot(slottingRxPalletRequest, httpHeaders);
  }

  @Counted(
      name = "rxAcquireSlotHitCount",
      level1 = "uwms-receiving",
      level2 = "rxSlottingServiceImpl",
      level3 = "rxAcquireSlot")
  @Timed(
      name = "acquireSlotTimes",
      level1 = "uwms-receiving",
      level2 = "rxSlottingServiceImpl",
      level3 = "acquireSlot")
  @ExceptionCounted(
      name = "acquireSlotExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rxSlottingServiceImpl",
      level3 = "acquireSlot")
  public SlottingPalletResponse acquireSlotMultiPallets(
      String messageId,
      int locationSize,
      List<String> lpnTrackingIds,
      List<Instruction> validInstructionList,
      String manualSlot,
      HttpHeaders httpHeaders) {

    Instruction firstInstruction = validInstructionList.get(0);
    boolean isPartialInstruction =
        RxUtils.isPartialInstruction(firstInstruction.getInstructionCode());
    String door =
        firstInstruction.getMove().containsKey(ReceivingConstants.MOVE_FROM_LOCATION)
            ? (String) firstInstruction.getMove().get(ReceivingConstants.MOVE_FROM_LOCATION)
            : null;

    String findSlotType =
        isPartialInstruction
            ? ReceivingConstants.SLOTTING_FIND_PRIME_SLOT
            : ReceivingConstants.SLOTTING_FIND_SLOT;
    httpHeaders.set(ReceivingConstants.SLOTTING_FEATURE_TYPE, findSlotType);
    List<SlottingContainerDetails> slottingRxContainerDetailsList = new ArrayList<>();

    for (int i = 0; i < validInstructionList.size(); i++) {
      Instruction instruction = validInstructionList.get(i);
      DeliveryDocumentLine deliveryDocumentLine =
          InstructionUtils.getDeliveryDocumentLine(instruction);
      String qtyUom = isPartialInstruction ? Uom.WHPK : Uom.VNPK;
      int qty =
          ReceivingUtils.conversionToUOM(
              instruction.getReceivedQuantity(),
              instruction.getReceivedQuantityUOM(),
              qtyUom,
              deliveryDocumentLine.getVendorPack(),
              deliveryDocumentLine.getWarehousePack());

      SlottingContainerItemDetails slottingContainerItemDetails =
          new SlottingContainerItemDetails();
      slottingContainerItemDetails.setItemNbr(deliveryDocumentLine.getItemNbr());
      slottingContainerItemDetails.setQty(qty);
      slottingContainerItemDetails.setQtyUom(qtyUom);
      slottingContainerItemDetails.setItemUPC(deliveryDocumentLine.getGtin());

      List<SlottingContainerItemDetails> slottingContainerItemDetailsList = new ArrayList<>();
      slottingContainerItemDetailsList.add(slottingContainerItemDetails);

      SlottingContainerDetails slottingContainerDetails = new SlottingContainerDetails();
      slottingContainerDetails.setContainerTrackingId(lpnTrackingIds.get(i));
      if (locationSize > 0) {
        slottingContainerDetails.setLocationSize(locationSize);
      }
      slottingContainerDetails.setContainerItemsDetails(slottingContainerItemDetailsList);
      slottingContainerDetails.setFromLocation(door);
      if (StringUtils.isNotBlank(manualSlot)) slottingContainerDetails.setLocationName(manualSlot);
      slottingRxContainerDetailsList.add(slottingContainerDetails);
    }

    SlottingPalletRequest slottingRxPalletRequest = new SlottingPalletRequest();
    slottingRxPalletRequest.setMessageId(messageId);
    slottingRxPalletRequest.setContainerDetails(slottingRxContainerDetailsList);
    return slottingRestApiClient.getSlot(slottingRxPalletRequest, httpHeaders);
  }

  /**
   * Free Slot
   *
   * @param itemNumber
   * @param slotId
   * @param httpHeaders
   */
  @Counted(
      name = "rxFreeSlotHitCount",
      level1 = "uwms-receiving",
      level2 = "rxSlottingServiceImpl",
      level3 = "rxFreeSlot")
  @Timed(
      name = "rxFreeSlotTimed",
      level1 = "uwms-receiving",
      level2 = "rxSlottingServiceImpl",
      level3 = "rxFreeSlot")
  @ExceptionCounted(
      name = "rxFreeSlotExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rxSlottingServiceImpl",
      level3 = "rxFreeSlot")
  public void freeSlot(Long itemNumber, String slotId, HttpHeaders httpHeaders) {
    try {
      // Ignoring this error because, there is a background job running which will free up the slot
      // if this fails
      // no need to block or cancel the invoking operations because of this failure
      slottingRestApiClient.freeSlot(itemNumber, slotId, httpHeaders);
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
    }
  }
}
