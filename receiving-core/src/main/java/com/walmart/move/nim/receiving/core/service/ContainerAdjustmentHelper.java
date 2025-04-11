package com.walmart.move.nim.receiving.core.service;

import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.CancelContainerResponse;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ContainerAdjustmentHelper {

  private static final Logger LOGGER = LoggerFactory.getLogger(ContainerAdjustmentHelper.class);

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  private DeliveryService deliveryService;

  @Autowired private ReceiptService receiptService;
  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private ContainerAdjustmentValidator containerAdjustmentValidator;

  private JsonParser jsonParser = new JsonParser();

  public CancelContainerResponse validateContainerForAdjustment(
      Container container, HttpHeaders httpHeaders) {
    LOGGER.info(
        "Validating container can be adjusted for trackingId :{}", container.getTrackingId());
    CancelContainerResponse cancelContainerResponse = null;
    cancelContainerResponse =
        validateDeliveryStatusForLabelAdjustment(
            container.getTrackingId(), container.getDeliveryNumber(), httpHeaders);
    if (Objects.isNull(cancelContainerResponse)) {
      cancelContainerResponse =
          containerAdjustmentValidator.validateContainerAdjustmentForParentContainer(container);
    }
    return cancelContainerResponse;
  }

  public Receipt adjustReceipts(Container container) {
    Receipt adjustedReceipt = null;
    ContainerItem containerItem = container.getContainerItems().get(0);

    int finalVnpkQty =
        ReceivingUtils.conversionToVendorPack(
            containerItem.getQuantity(),
            containerItem.getQuantityUOM(),
            containerItem.getVnpkQty(),
            containerItem.getWhpkQty());
    adjustedReceipt = adjustReceipts(container, finalVnpkQty, containerItem.getQuantity());
    return adjustedReceipt;
  }

  @Transactional
  @InjectTenantFilter
  public void persistAdjustedReceiptsAndContainer(Receipt receipt, Container container) {
    if (ObjectUtils.allNotNull(receipt, container)) {
      receiptService.saveReceipt(receipt);
      containerPersisterService.saveContainer(container);
    }
  }

  public Container adjustPalletQuantity(
      Integer adjustByQtyInEaches, Container container, String userId) {
    ContainerItem containerItem = container.getContainerItems().get(0);
    containerItem.setQuantity(adjustByQtyInEaches);
    container.setContainerItems(Collections.singletonList(containerItem));
    container.setLastChangedUser(userId);
    return container;
  }

  public Receipt adjustQuantityInReceipt(
      Integer newQuantity, String newQuantityUom, Container container, String userId) {

    ContainerItem containerItem = container.getContainerItems().get(0);

    Integer currentContainerItemQtyInVnpk =
        ReceivingUtils.conversionToVendorPack(
            containerItem.getQuantity(),
            containerItem.getQuantityUOM(),
            containerItem.getVnpkQty(),
            containerItem.getWhpkQty());

    Integer newContainerQtyInVnpk =
        ReceivingUtils.conversionToVendorPack(
            newQuantity, newQuantityUom, containerItem.getVnpkQty(), containerItem.getWhpkQty());

    Integer diffQtyInVnpk =
        newQuantity > 0
            ? currentContainerItemQtyInVnpk - newContainerQtyInVnpk
            : currentContainerItemQtyInVnpk + newContainerQtyInVnpk;

    Integer newContainerQtyInEA =
        ReceivingUtils.conversionToEaches(
            newQuantity, newQuantityUom, containerItem.getVnpkQty(), containerItem.getWhpkQty());

    Integer currentContainerItemQtyInEA =
        ReceivingUtils.conversionToEaches(
            containerItem.getQuantity(),
            containerItem.getQuantityUOM(),
            containerItem.getVnpkQty(),
            containerItem.getWhpkQty());

    Integer diffQtyInEaches =
        newContainerQtyInEA > 0
            ? currentContainerItemQtyInEA - newContainerQtyInEA
            : currentContainerItemQtyInEA + newContainerQtyInEA;

    Receipt receipt = new Receipt();
    receipt.setDeliveryNumber(container.getDeliveryNumber());
    receipt.setDoorNumber(container.getLocation());
    receipt.setPurchaseReferenceNumber(containerItem.getPurchaseReferenceNumber());
    receipt.setPurchaseReferenceLineNumber(containerItem.getPurchaseReferenceLineNumber());
    receipt.setQuantity(diffQtyInVnpk * -1);
    receipt.setQuantityUom(ReceivingConstants.Uom.VNPK);
    receipt.setVnpkQty(containerItem.getVnpkQty());
    receipt.setWhpkQty(containerItem.getWhpkQty());
    receipt.setEachQty(diffQtyInEaches * -1);
    receipt.setCreateUserId(userId);

    return receipt;
  }

  public CancelContainerResponse validateDeliveryStatusForLabelAdjustment(
      String trackingId, Long deliveryNumber, HttpHeaders httpHeaders) {
    LOGGER.info(
        "Entering validateDeliveryStatusForLabelAdjustment() with delivery:{} and trackingId:{}",
        deliveryNumber,
        trackingId);
    CancelContainerResponse cancelContainerResponse = null;
    try {
      String deliveryResponse =
          deliveryService.getDeliveryByDeliveryNumber(deliveryNumber, httpHeaders);
      String deliveryStatus =
          jsonParser.parse(deliveryResponse).getAsJsonObject().get("deliveryStatus").getAsString();
      if (DeliveryStatus.FNL.name().equals(deliveryStatus)) {
        LOGGER.error(
            "Delivery: {} is in FNL status, label correction or cancellation is not allowed",
            deliveryNumber);
        return new CancelContainerResponse(
            trackingId,
            ExceptionCodes.LABEL_CORRECTION_ERROR_FOR_FINALIZED_DELIVERY,
            ReceivingException.LABEL_QUANTITY_ADJUSTMENT_ERROR_MSG_FOR_FINALIZED_DELIVERY);
      }
    } catch (ReceivingException e) {
      LOGGER.error(
          "Error while fetching delivery from GDM by deliveryNumber :{}", deliveryNumber, e);
      return new CancelContainerResponse(
          trackingId, ExceptionCodes.GDM_NOT_ACCESSIBLE, ReceivingException.GDM_SERVICE_DOWN);
    }

    return cancelContainerResponse;
  }

  @Transactional
  @InjectTenantFilter
  public void persistAdjustedReceiptsAndContainers(Receipt receipt, List<Container> containers) {
    if (ObjectUtils.allNotNull(receipt, containers)) {
      receiptService.saveReceipt(receipt);

      List<ContainerItem> containerItems =
          containers
              .stream()
              .map(container -> container.getContainerItems())
              .filter(CollectionUtils::isNotEmpty)
              .flatMap(List::stream)
              .collect(Collectors.toList());

      containerPersisterService.saveContainerAndContainerItems(containers, containerItems);
    }
  }

  /**
   * Prepare receipts with quantity
   *
   * @param container
   * @param finalVnpkQty
   * @param containerQuantity
   * @return
   */
  public Receipt adjustReceipts(Container container, int finalVnpkQty, int containerQuantity) {
    Receipt adjustedReceipt = null;
    ContainerItem containerItem = container.getContainerItems().get(0);

    if (0 != finalVnpkQty) {
      finalVnpkQty = finalVnpkQty * -1;

      // Since this is cancel label, we need to store receipt value in -ve
      int finalEachQty = containerQuantity * -1;

      Receipt receipt = new Receipt();
      receipt.setDeliveryNumber(container.getDeliveryNumber());
      receipt.setDoorNumber(container.getLocation());
      receipt.setPurchaseReferenceNumber(containerItem.getPurchaseReferenceNumber());
      receipt.setPurchaseReferenceLineNumber(containerItem.getPurchaseReferenceLineNumber());
      receipt.setQuantity(finalVnpkQty);
      receipt.setQuantityUom(ReceivingConstants.Uom.VNPK);
      receipt.setVnpkQty(containerItem.getVnpkQty());
      receipt.setWhpkQty(containerItem.getWhpkQty());
      receipt.setEachQty(finalEachQty);
      receipt.setCreateUserId(container.getCreateUser());

      adjustedReceipt = receipt;
    }
    return adjustedReceipt;
  }
}
