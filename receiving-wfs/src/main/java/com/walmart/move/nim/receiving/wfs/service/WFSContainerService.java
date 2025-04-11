package com.walmart.move.nim.receiving.wfs.service;

import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.service.AbstractContainerService;
import com.walmart.move.nim.receiving.core.service.ContainerAdjustmentValidator;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public class WFSContainerService extends AbstractContainerService {
  private static final Logger log = LoggerFactory.getLogger(WFSContainerService.class);
  @Autowired ContainerRepository containerRepository;
  @Autowired TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private ReceiptService receiptService;

  @Autowired private DeliveryStatusPublisher deliveryStatusPublisher;

  @Autowired private ContainerPersisterService containerPersisterService;

  @Autowired private ContainerAdjustmentValidator containerAdjustmentValidator;

  @Autowired private ContainerItemRepository containerItemRepository;

  /**
   * Backout the container for WFS
   *
   * @param trackingId
   * @param headers
   * @throws ReceivingException
   */
  @ExceptionCounted(
      name = "backoutReceiptsForWFSExceptionCount",
      level1 = "uwms-receiving",
      level2 = "containerService",
      level3 = "backoutContainers")
  public void backoutContainerForWFS(String trackingId, HttpHeaders headers)
      throws ReceivingException {
    log.info("Entering backoutContainerForWFS() with trackingId[{}]", trackingId);

    Container container = containerAdjustmentValidator.getValidContainer(trackingId);

    /*
     * Create receipts with negative quantity
     */
    Long deliveryNumber = container.getDeliveryNumber();
    String userId = headers.get(ReceivingConstants.USER_ID_HEADER_KEY).get(0);
    final List<Receipt> receipts = new ArrayList<>(container.getContainerItems().size());
    container
        .getContainerItems()
        .forEach(
            containerItem -> {
              Receipt receipt = new Receipt();
              receipt.setDeliveryNumber(deliveryNumber);
              receipt.setPurchaseReferenceNumber(containerItem.getPurchaseReferenceNumber());
              receipt.setPurchaseReferenceLineNumber(
                  containerItem.getPurchaseReferenceLineNumber());
              receipt.setVnpkQty(containerItem.getVnpkQty());
              receipt.setWhpkQty(containerItem.getWhpkQty());
              receipt.setEachQty(containerItem.getQuantity() * -1);
              receipt.setQuantity(containerItem.getQuantity() * -1);
              receipt.setQuantityUom(ReceivingConstants.Uom.EACHES);
              receipt.setCreateUserId(userId);
              receipt.setCreateTs(new Date());
              receipts.add(receipt);
            });

    /*
     * Updating container Status and saving receipts
     */

    containerPersisterService.updateContainerStatusAndSaveReceipts(
        trackingId, ReceivingConstants.STATUS_BACKOUT, userId, receipts);

    checkAndRepublishOsdrIfNecessary(deliveryNumber, headers);

    log.info("Given container for WFS [{}] backout successfully", container.getTrackingId());
  }

  public void checkAndRepublishOsdrIfNecessary(Long deliveryNumber, HttpHeaders headers)
      throws ReceivingException {
    DeliveryService deliveryService =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.DELIVERY_SERVICE_KEY,
            DeliveryService.class);
    String deliveryResponse = deliveryService.getDeliveryByDeliveryNumber(deliveryNumber, headers);
    DeliveryDetails deliveryDetails =
        JacksonParser.convertJsonToObject(deliveryResponse, DeliveryDetails.class);

    /*
     * Publish delivery status with list of received quantity
     */
    if (InstructionUtils.isReceiptPostingRequired(
        deliveryDetails.getDeliveryStatus(), deliveryDetails.getStateReasonCodes())) {
      deliveryStatusPublisher.publishDeliveryStatus(
          deliveryNumber,
          DeliveryStatus.COMPLETE.name(),
          receiptService.getReceivedQtySummaryByPOForDelivery(
              deliveryNumber, ReceivingConstants.Uom.EACHES),
          ReceivingUtils.getForwardablHeader(headers));
    }
  }

  public Receipt createDiffReceipt(
      Container container,
      ContainerItem containerItem,
      Integer diffQuantityInEaches,
      String userId) {
    Receipt adjustedReceipt = new Receipt();
    adjustedReceipt.setCreateTs(Date.from(Instant.now()));
    adjustedReceipt.setDeliveryNumber(container.getDeliveryNumber());
    adjustedReceipt.setDoorNumber(container.getLocation());
    adjustedReceipt.setEachQty(diffQuantityInEaches);
    adjustedReceipt.setFacilityCountryCode(containerItem.getFacilityCountryCode());
    adjustedReceipt.setFacilityNum(containerItem.getFacilityNum());
    adjustedReceipt.setPurchaseReferenceLineNumber(containerItem.getPurchaseReferenceLineNumber());
    adjustedReceipt.setPurchaseReferenceNumber(containerItem.getPurchaseReferenceNumber());
    adjustedReceipt.setCreateUserId(userId);

    adjustedReceipt.setQuantity(diffQuantityInEaches);
    adjustedReceipt.setQuantityUom(ReceivingConstants.Uom.EACHES);
    adjustedReceipt.setVnpkQty(containerItem.getVnpkQty());
    adjustedReceipt.setWhpkQty(containerItem.getWhpkQty());
    return adjustedReceipt;
  }

  public Integer adjustContainerItemQuantityAndGetDiff(
      String cId,
      String userId,
      Integer newQuantityInUI,
      ContainerUpdateResponse response,
      Container container,
      ContainerItem containerItem,
      Integer quantityInINV,
      String uom)
      throws ReceivingException {
    log.info("cId={} adjust Quantity In Container", cId);
    Integer newQuantityInEaches_UI =
        ReceivingUtils.conversionToEaches(
            newQuantityInUI, uom, containerItem.getVnpkQty(), containerItem.getWhpkQty());

    final Integer quantityInEaches_INV =
        ReceivingUtils.conversionToEaches(
            quantityInINV, uom, containerItem.getVnpkQty(), containerItem.getWhpkQty());

    final Integer quantityInEaches_RCV =
        ReceivingUtils.conversionToEaches(
            containerItem.getQuantity(),
            containerItem.getQuantityUOM(),
            containerItem.getVnpkQty(),
            containerItem.getWhpkQty());
    Integer diffQuantityInEaches_RCV;
    Integer newQuantityInEaches_RCV;
    final boolean isQuantitySyncRcvAndInv = (quantityInEaches_RCV - quantityInEaches_INV) == 0;
    if (isQuantitySyncRcvAndInv) {
      diffQuantityInEaches_RCV = newQuantityInEaches_UI - quantityInEaches_RCV;
      newQuantityInEaches_RCV = newQuantityInEaches_UI;
    } else {
      log.info(
          "Quantity is out of sync between Inventory={} and Receiving={} in eaches",
          quantityInEaches_INV,
          quantityInEaches_RCV);
      diffQuantityInEaches_RCV = newQuantityInEaches_UI - quantityInEaches_INV;
      newQuantityInEaches_RCV = quantityInEaches_RCV + diffQuantityInEaches_RCV;
    }

    if (diffQuantityInEaches_RCV == 0) {
      log.error(
          "requested newQuantity={}, is same as currentQuantity={}",
          newQuantityInUI,
          quantityInEaches_RCV);
      throw new ReceivingException(
          ReceivingException.ADJUST_PALLET_QUANTITY_SAME_ERROR_MSG,
          HttpStatus.BAD_REQUEST,
          ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_CODE,
          ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_HEADER);
    }

    // containerItem, container updates
    container = ContainerUtils.adjustContainerByQtyWithoutTiHi(container, newQuantityInEaches_RCV);
    containerItem = container.getContainerItems().get(0);
    container.setLastChangedUser(userId);
    containerItemRepository.save(containerItem);
    // 0 new quantity will delete in inventory so mark as backout
    if (newQuantityInUI == 0) {
      log.info("cId={}, pallet correction to 0 sets container to backout i.e lpn-cancel", cId);
      container.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);
    }
    containerPersisterService.saveContainer(container);

    response.setContainer(container);
    return diffQuantityInEaches_RCV;
  }
}
