package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.getForwardableHttpHeaders;

import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceiveContainersRequestBody;
import com.walmart.move.nim.receiving.core.client.slotting.SlottingRestApiClient;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.slotting.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.Counted;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
public class SlottingServiceImpl {

  private static final Logger LOGGER = LoggerFactory.getLogger(SlottingServiceImpl.class);
  @Autowired private SlottingRestApiClient slottingRestApiClient;

  @Counted(
      name = "receivePalletSlottingHitCount",
      level1 = "uwms-receiving",
      level2 = "slottingServiceImpl",
      level3 = "receivePallet")
  @Timed(
      name = "receivePalletSlottingTimed",
      level1 = "uwms-receiving",
      level2 = "slottingServiceImpl",
      level3 = "receivePallet")
  @ExceptionCounted(
      name = "receivePalletSlottingExceptionCount",
      level1 = "uwms-receiving",
      level2 = "slottingServiceImpl",
      level3 = "receivePallet")
  public SlottingPalletResponse receivePallet(
      ReceiveInstructionRequest receiveInstructionRequest,
      String labelTrackingId,
      HttpHeaders httpHeaders,
      ReceiveContainersRequestBody receiveContainersRequestBody) {

    httpHeaders = getForwardableHttpHeaders(httpHeaders);
    SlottingPalletResponse response = null;
    httpHeaders.set(
        ReceivingConstants.SLOTTING_FEATURE_TYPE, ReceivingConstants.SLOTTING_FIND_SLOT);
    DeliveryDocumentLine deliveryDocumentLine =
        receiveInstructionRequest.getDeliveryDocumentLines().get(0);
    SlottingPalletRequest slottingPalletRequest = new SlottingPalletRequest();
    SlottingContainerDetails slottingContainerDetails = new SlottingContainerDetails();
    SlottingContainerItemDetails slottingContainerItemDetails = new SlottingContainerItemDetails();
    List<SlottingContainerDetails> slottingContainerDetailsList = new ArrayList<>();
    List<SlottingContainerItemDetails> slottingContainerItemDetailsList = new ArrayList<>();

    slottingPalletRequest.setDeliveryNumber(receiveInstructionRequest.getDeliveryNumber());
    if (StringUtils.isNotBlank(labelTrackingId)) {
      slottingContainerDetails.setContainerTrackingId(labelTrackingId);
    }

    SlotDetails slotDetails = receiveInstructionRequest.getSlotDetails();
    if (Objects.nonNull(slotDetails)) {
      if (Objects.nonNull(slotDetails.getSlotSize())) {
        slottingContainerDetails.setLocationSize(slotDetails.getSlotSize());
      }
      if (StringUtils.isNotBlank(slotDetails.getSlot())) {
        slottingContainerDetails.setLocationName(slotDetails.getSlot());
      }
    }
    slottingContainerItemDetails.setItemNbr(deliveryDocumentLine.getItemNbr());
    if (StringUtils.isNotBlank(deliveryDocumentLine.getAdditionalInfo().getHandlingCode())) {
      slottingContainerItemDetails.setHandlingMthdCode(
          deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    }
    if (StringUtils.isNotBlank(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode())) {
      slottingContainerItemDetails.setPackTypeCode(
          deliveryDocumentLine.getAdditionalInfo().getPackTypeCode());
    }

    Integer receiveQuantityInEaches =
        ReceivingUtils.conversionToEaches(
            receiveInstructionRequest.getQuantity(),
            ReceivingConstants.Uom.VNPK,
            deliveryDocumentLine.getVendorPack(),
            deliveryDocumentLine.getWarehousePack());
    slottingContainerItemDetails.setQty(receiveQuantityInEaches);
    slottingContainerItemDetails.setQtyUom(ReceivingConstants.Uom.EACHES);
    slottingContainerItemDetails.setVnpkRatio(deliveryDocumentLine.getVendorPack());
    slottingContainerItemDetails.setWhpkRatio(deliveryDocumentLine.getWarehousePack());
    // Check if Purchase Ref corresponds to DA Flow
    boolean isDaItem =
        ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(
            deliveryDocumentLine.getPurchaseRefType());

    if (isDaItem && Objects.nonNull(slotDetails)) {
      slottingContainerItemDetails.setStockType(slotDetails.getStockType());
    }
    slottingContainerItemDetailsList.add(slottingContainerItemDetails);
    slottingContainerDetails.setContainerItemsDetails(slottingContainerItemDetailsList);
    slottingContainerDetailsList.add(slottingContainerDetails);

    // slotting request with rds containers payload
    if (Objects.nonNull(receiveContainersRequestBody)) {
      SlottingPalletRequestWithRdsPayLoad slottingPalletRequestWithRdsPayLoad =
          new SlottingPalletRequestWithRdsPayLoad();
      slottingPalletRequestWithRdsPayLoad.setAtlasOnboardedItem(
          deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem());
      slottingPalletRequestWithRdsPayLoad.setRds(receiveContainersRequestBody);
      slottingPalletRequest = slottingPalletRequestWithRdsPayLoad;
    }

    if (isDaItem && Objects.nonNull(slotDetails)) {
      slottingPalletRequest.setReceivingMethod(ReceivingConstants.PURCHASE_REF_TYPE_DA);
      slottingPalletRequest.setMaxPalletPerSlot(slotDetails.getMaxPallet());
      slottingPalletRequest.setCrossReference(slotDetails.getCrossReferenceDoor());
    } else {
      slottingPalletRequest.setReceivingMethod(ReceivingConstants.SLOTTING_SSTK_RECEIVING_METHOD);
    }
    slottingPalletRequest.setMessageId(TenantContext.getCorrelationId());
    slottingPalletRequest.setDoorId(receiveInstructionRequest.getDoorNumber());
    slottingPalletRequest.setContainerDetails(slottingContainerDetailsList);

    response = slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);
    return response;
  }

  @Counted(
      name = "getPrimeSlotByDocumentLineHitCount",
      level1 = "uwms-receiving",
      level2 = "slottingServiceImpl",
      level3 = "getPrimeSlotByDocumentLine")
  @Timed(
      name = "getPrimeSlotByDocumentLineTimed",
      level1 = "uwms-receiving",
      level2 = "slottingServiceImpl",
      level3 = "getPrimeSlotByDocumentLine")
  @ExceptionCounted(
      name = "getPrimeSlotByDocumentLineExceptionCount",
      level1 = "uwms-receiving",
      level2 = "slottingServiceImpl",
      level3 = "getPrimeSlotByDocumentLine")
  public SlottingPalletResponse getPrimeSlot(
      DeliveryDocumentLine deliveryDocumentLine, String messageId, HttpHeaders httpHeaders) {

    httpHeaders.set(
        ReceivingConstants.SLOTTING_FEATURE_TYPE, ReceivingConstants.SLOTTING_FIND_PRIME_SLOT);

    SlottingPalletRequest slottingPalletRequest = new SlottingPalletRequest();
    SlottingContainerDetails slottingContainerDetails = new SlottingContainerDetails();
    SlottingContainerItemDetails slottingContainerItemDetails = new SlottingContainerItemDetails();
    List<SlottingContainerDetails> slottingContainerDetailsList = new ArrayList<>();
    List<SlottingContainerItemDetails> slottingContainerItemDetailsList = new ArrayList<>();

    slottingPalletRequest.setReceivingMethod(ReceivingConstants.SLOTTING_SSTK_RECEIVING_METHOD);
    slottingPalletRequest.setMessageId(messageId);

    slottingContainerItemDetails.setItemNbr(deliveryDocumentLine.getItemNbr());
    if (StringUtils.isNotBlank(deliveryDocumentLine.getAdditionalInfo().getHandlingCode())) {
      slottingContainerItemDetails.setHandlingMthdCode(
          deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    }
    if (StringUtils.isNotBlank(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode())) {
      slottingContainerItemDetails.setPackTypeCode(
          deliveryDocumentLine.getAdditionalInfo().getPackTypeCode());
    }
    slottingContainerItemDetailsList.add(slottingContainerItemDetails);
    slottingContainerDetails.setContainerItemsDetails(slottingContainerItemDetailsList);
    slottingContainerDetailsList.add(slottingContainerDetails);
    slottingPalletRequest.setContainerDetails(slottingContainerDetailsList);

    return slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);
  }

  @Counted(
      name = "getPrimeSlotByItemNumbersHitCount",
      level1 = "uwms-receiving",
      level2 = "slottingServiceImpl",
      level3 = "getPrimeSlot")
  @Timed(
      name = "getPrimeSlotByItemNumbersTimed",
      level1 = "uwms-receiving",
      level2 = "slottingServiceImpl",
      level3 = "getPrimeSlot")
  @ExceptionCounted(
      name = "getPrimeSlotByItemNumbersExceptionCount",
      level1 = "uwms-receiving",
      level2 = "slottingServiceImpl",
      level3 = "getPrimeSlot")
  public SlottingPalletResponse getPrimeSlot(
      List<ContainerItem> containerItems, HttpHeaders httpHeaders) {

    String messageId = httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY);
    httpHeaders.set(
        ReceivingConstants.SLOTTING_FEATURE_TYPE, ReceivingConstants.SLOTTING_FIND_PRIME_SLOT);
    httpHeaders.set(ReceivingConstants.SOURCE, ReceivingConstants.DECANT_API);

    SlottingPalletRequest slottingPalletRequest = new SlottingPalletRequest();
    SlottingContainerDetails slottingContainerDetails = new SlottingContainerDetails();
    List<SlottingContainerDetails> slottingContainerDetailsList = new ArrayList<>();
    List<SlottingContainerItemDetails> slottingContainerItemDetailsList = new ArrayList<>();

    for (ContainerItem item : containerItems) {
      SlottingContainerItemDetails slottingContainerItemDetails =
          new SlottingContainerItemDetails();
      slottingContainerItemDetails.setItemNbr(item.getItemNumber());
      slottingContainerItemDetailsList.add(slottingContainerItemDetails);
    }

    slottingPalletRequest.setReceivingMethod(ReceivingConstants.SLOTTING_SSTK_RECEIVING_METHOD);
    slottingPalletRequest.setMessageId(messageId);
    slottingContainerDetails.setContainerItemsDetails(slottingContainerItemDetailsList);
    slottingContainerDetailsList.add(slottingContainerDetails);
    slottingPalletRequest.setContainerDetails(slottingContainerDetailsList);

    return slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);
  }

  /**
   * This method gets prime slot from smart slotting for split pallet items.
   *
   * @param deliveryDocumentLine
   * @param existingSplitPalletInstructionDeliveryDocuments
   * @param messageId
   * @param httpHeaders
   * @return
   */
  public SlottingPalletResponse getPrimeSlotForSplitPallet(
      DeliveryDocumentLine deliveryDocumentLine,
      List<DeliveryDocument> existingSplitPalletInstructionDeliveryDocuments,
      String messageId,
      HttpHeaders httpHeaders) {

    httpHeaders.set(
        ReceivingConstants.SLOTTING_FEATURE_TYPE,
        ReceivingConstants.SLOTTING_VALIDATE_PRIME_SLOT_FOR_SPLIT_PALLET);

    SlottingPalletRequest slottingPalletRequest = new SlottingPalletRequest();
    SlottingContainerDetails slottingContainerDetails = new SlottingContainerDetails();
    List<SlottingContainerDetails> slottingContainerDetailsList = new ArrayList<>();
    List<SlottingContainerItemDetails> slottingContainerItemDetailsList = new ArrayList<>();

    slottingPalletRequest.setReceivingMethod(ReceivingConstants.SLOTTING_SSTK_RECEIVING_METHOD);
    slottingPalletRequest.setMessageId(messageId);
    slottingContainerItemDetailsList.add(
        getSlottingContainerRequestItemDetails(deliveryDocumentLine));

    if (!CollectionUtils.isEmpty(existingSplitPalletInstructionDeliveryDocuments)) {
      existingSplitPalletInstructionDeliveryDocuments.forEach(
          existingSplitPalletInstructionDeliveryDocument -> {
            SlottingContainerItemDetails slottingContainerItemDetails =
                getSlottingContainerRequestItemDetails(
                    existingSplitPalletInstructionDeliveryDocument
                        .getDeliveryDocumentLines()
                        .get(0));
            slottingContainerItemDetailsList.add(slottingContainerItemDetails);
          });
    }

    slottingContainerDetails.setContainerItemsDetails(slottingContainerItemDetailsList);
    slottingContainerDetailsList.add(slottingContainerDetails);
    slottingPalletRequest.setContainerDetails(slottingContainerDetailsList);

    List<ItemData> itemDataList =
        existingSplitPalletInstructionDeliveryDocuments
            .stream()
            .parallel()
            .map(document -> document.getDeliveryDocumentLines().get(0).getAdditionalInfo())
            .collect(Collectors.toList());

    return slottingRestApiClient.getPrimeSlotForSplitPallet(
        slottingPalletRequest, itemDataList, httpHeaders);
  }

  private SlottingContainerItemDetails getSlottingContainerRequestItemDetails(
      DeliveryDocumentLine deliveryDocumentLine) {
    SlottingContainerItemDetails slottingContainerItemDetails = new SlottingContainerItemDetails();
    slottingContainerItemDetails.setItemNbr(deliveryDocumentLine.getItemNbr());
    if (StringUtils.isNotBlank(deliveryDocumentLine.getAdditionalInfo().getHandlingCode())) {
      slottingContainerItemDetails.setHandlingMthdCode(
          deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    }
    if (StringUtils.isNotBlank(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode())) {
      slottingContainerItemDetails.setPackTypeCode(
          deliveryDocumentLine.getAdditionalInfo().getPackTypeCode());
    }
    return slottingContainerItemDetails;
  }

  /**
   * Freeing Slot
   *
   * @param httpHeaders
   */
  @Counted(
      name = "freeSlotHitCount",
      level1 = "uwms-receiving",
      level2 = "slottingServiceImpl",
      level3 = "freeSlot")
  @Timed(
      name = "freeSlotTimed",
      level1 = "uwms-receiving",
      level2 = "slottingServiceImpl",
      level3 = "freeSlot")
  @ExceptionCounted(
      name = "freeSlotExceptionCount",
      level1 = "uwms-receiving",
      level2 = "slottingServiceImpl",
      level3 = "freeSlot")
  public void freeSlot(String messageId, String trackingId, String slot, HttpHeaders httpHeaders) {
    try {
      httpHeaders = getForwardableHttpHeaders(httpHeaders);
      httpHeaders.set(ReceivingConstants.SLOTTING_FEATURE_TYPE, ReceivingConstants.LABEL_BACKOUT);

      SlottingPalletRequest slottingPalletRequest = new SlottingPalletRequest();
      SlottingContainerDetails slottingContainerDetails = new SlottingContainerDetails();
      SlottingContainerItemDetails slottingContainerItemDetails =
          new SlottingContainerItemDetails();
      List<SlottingContainerDetails> slottingContainerDetailsList = new ArrayList<>();
      List<SlottingContainerItemDetails> slottingContainerItemDetailsList = new ArrayList<>();

      slottingContainerItemDetails.setLocation(slot);
      slottingContainerItemDetails.setStatus(ReceivingConstants.STATUS_CANCELLED);
      slottingContainerItemDetailsList.add(slottingContainerItemDetails);

      slottingContainerDetails.setContainerTrackingId(trackingId);
      slottingContainerDetails.setContainerItemsDetails(slottingContainerItemDetailsList);
      slottingContainerDetailsList.add(slottingContainerDetails);

      slottingPalletRequest.setMessageId(messageId);
      slottingPalletRequest.setContainerDetails(slottingContainerDetailsList);

      slottingRestApiClient.freeSlot(slottingPalletRequest, httpHeaders);
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
    }
  }
}
