package com.walmart.move.nim.receiving.endgame.service;

import static java.util.Collections.singletonList;
import static org.springframework.util.ObjectUtils.isEmpty;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrderLine;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.message.common.ScanEventData;
import com.walmart.move.nim.receiving.endgame.model.ReceiveVendorPack;
import com.walmart.move.nim.receiving.endgame.model.ReceivingRequest;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EndGameManualReceivingService extends EndGameReceivingService {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndGameManualReceivingService.class);

  /**
   * Method to receive "1 VendorPack"
   *
   * @param scanEventData
   * @return receiveVendorPack
   */
  public ReceiveVendorPack receiveVendorPack(ScanEventData scanEventData) {
    if (Objects.isNull(scanEventData.getPurchaseOrder())) {
      return super.receiveVendorPack(scanEventData);
    }
    ReceivingRequest receivingRequestScanEventData = (ReceivingRequest) scanEventData;
    Pair<PurchaseOrder, PurchaseOrderLine> selectedPoAndLine =
        getPurchaseOrderAndLineToReceive(
            singletonList(receivingRequestScanEventData.getPurchaseOrder()),
            Optional.ofNullable(receivingRequestScanEventData.getQuantity()),
            receivingRequestScanEventData.getDeliveryNumber());
    if (Objects.isNull(selectedPoAndLine)) {
      LOGGER.error(ReceivingException.PO_LINE_EXHAUSTED);
      throw new ReceivingConflictException(
          ExceptionCodes.PO_LINE_EXHAUSTED,
          String.format(
              EndgameConstants.PO_PO_LINE_EXHAUSTED_ERROR_MSG,
              scanEventData.getCaseUPC(),
              scanEventData.getDeliveryNumber()));
    }
    Integer quantity =
        Objects.nonNull(receivingRequestScanEventData.getQuantity())
            ? receivingRequestScanEventData.getQuantity()
            : 1;
    String quantityUOM =
        isEmpty(receivingRequestScanEventData.getQuantityUOM())
            ? selectedPoAndLine.getValue().getOrdered().getUom()
            : receivingRequestScanEventData.getQuantityUOM();
    int eachQuantity =
        ReceivingUtils.conversionToEaches(
            quantity,
            quantityUOM,
            selectedPoAndLine.getValue().getVnpk().getQuantity(),
            selectedPoAndLine.getValue().getWhpk().getQuantity());
    super.updateAuditEventInDeliveryMetadata(
        Collections.singletonList(receivingRequestScanEventData.getPurchaseOrder()),
        receivingRequestScanEventData.getDeliveryNumber(),
        receivingRequestScanEventData.getCaseUPC(),
        eachQuantity);
    isAuditRequired(selectedPoAndLine, scanEventData);
    return getReceiveVendorPack(scanEventData, selectedPoAndLine, eachQuantity);
  }

  /**
   * Method to receive a MultiSKU container. Item Quantity is required and variable. If a container
   * exists, the item is added to the container. If the container doesn't exist, a new container is
   * created.
   *
   * @param receivingRequest
   * @return receiveVendorPack
   */
  public ReceiveVendorPack receiveMultiSKUContainer(ReceivingRequest receivingRequest) {
    if (Objects.isNull(receivingRequest.getPurchaseOrder())) {
      return super.receiveMultiSKUContainer(receivingRequest);
    }
    /*
     MultiSKU will get receive in eaches only
    */
    Pair<PurchaseOrder, PurchaseOrderLine> selectedPoAndLine =
        getPurchaseOrderAndLineToReceive(
            singletonList(receivingRequest.getPurchaseOrder()),
            Optional.of(receivingRequest.getQuantity()),
            receivingRequest.getDeliveryNumber());

    if (Objects.isNull(selectedPoAndLine)) {
      LOGGER.error(ReceivingException.PO_LINE_EXHAUSTED);
      throw new ReceivingConflictException(
          ExceptionCodes.PO_LINE_EXHAUSTED,
          String.format(
              EndgameConstants.PO_PO_LINE_EXHAUSTED_ERROR_MSG,
              receivingRequest.getCaseUPC(),
              receivingRequest.getDeliveryNumber()));
    }
    TenantContext.setAdditionalParams("hasMultiSKU", Boolean.TRUE);
    Container container =
        createAndPublishMultiSKUContainer(
            receivingRequest,
            selectedPoAndLine.getKey(),
            selectedPoAndLine.getValue(),
            receivingRequest.getQuantity());

    if (!tenantSpecificConfigReader.isOutboxEnabledForInventory()) {
      postToDCFin(singletonList(container), selectedPoAndLine.getKey().getLegacyType(), null);
    }
    return ReceiveVendorPack.builder().container(container).build();
  }
}
