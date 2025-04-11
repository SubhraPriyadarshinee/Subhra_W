package com.walmart.move.nim.receiving.mfc.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DUMMY_PURCHASE_REF_NUMBER;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.mfc.model.common.CommonReceiptDTO;
import com.walmart.move.nim.receiving.mfc.model.common.Quantity;
import com.walmart.move.nim.receiving.mfc.model.common.QuantityType;
import com.walmart.move.nim.receiving.mfc.utils.MFCUtils;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class MFCReceiptService {

  private static final Logger LOGGER = LoggerFactory.getLogger(MFCReceiptService.class);

  @Autowired private ReceiptService receiptService;

  @Timed(
      name = "mfcReceiptCreationTimed",
      level1 = "uwms-receiving-api",
      level2 = "mfcReceiptService")
  @ExceptionCounted(
      name = "mfcReceiptCreationExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "mfcReceiptService")
  public List<Receipt> saveReceipt(List<Receipt> receiptList) {
    LOGGER.info("Going to save receiptList : {}", receiptList);
    return receiptService.saveAll(receiptList);
  }

  public Receipt createReceipt(
      Container container, ContainerItem containerItem, CommonReceiptDTO commonReceiptDTO) {

    Quantity quantity =
        Quantity.builder()
            .type(QuantityType.DECANTED)
            .value(containerItem.getOrderFilledQty())
            .uom(containerItem.getOrderFilledQtyUom())
            .build();
    Optional<Quantity> quantityOptional = commonReceiptDTO.getQuantities().stream().findAny();
    if (quantityOptional.isPresent()) {
      quantity = quantityOptional.get();
      LOGGER.info("Quantity selected with quantity = {}", quantity);
    }

    Receipt receipt = new Receipt();
    receipt.setDeliveryNumber(container.getDeliveryNumber());
    receipt.setInvoiceNumber(containerItem.getInvoiceNumber());
    receipt.setInvoiceLineNumber(containerItem.getInvoiceLineNumber());
    receipt.setInboundShipmentDocId(container.getShipmentId());

    receipt.setVnpkQty(containerItem.getVnpkQty());
    receipt.setWhpkQty(containerItem.getWhpkQty());
    receipt.setCreateUserId(ReceivingUtils.retrieveUserId());
    receipt.setCreateTs(new Date());
    MFCUtils.setQuantity(
        receipt,
        quantity.getType(),
        containerItem.getOrderFilledQty().intValue(),
        containerItem.getOrderFilledQtyUom());
    receipt.setPurchaseReferenceNumber(DUMMY_PURCHASE_REF_NUMBER);
    return receipt;
  }
}
