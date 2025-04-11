package com.walmart.move.nim.receiving.sib.mapper;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import java.util.ArrayList;
import java.util.List;

public class ReceiptMapper {

  public static List<Receipt> getReceipts(List<Container> containers) {
    List<Receipt> receipts = new ArrayList<>();
    containers.forEach(
        container -> {
          container
              .getContainerItems()
              .forEach(
                  containerItem -> {
                    Receipt receipt = new Receipt();
                    receipt.setDeliveryNumber(container.getDeliveryNumber());
                    receipt.setInvoiceNumber(containerItem.getInvoiceNumber());
                    receipt.setInvoiceLineNumber(containerItem.getInvoiceLineNumber());
                    receipt.setInboundShipmentDocId(container.getShipmentId());

                    receipt.setVnpkQty(containerItem.getVnpkQty());
                    receipt.setWhpkQty(containerItem.getWhpkQty());
                    receipt.setCreateUserId(ReceivingUtils.retrieveUserId());
                    receipt.setQuantity(containerItem.getQuantity());
                    receipt.setEachQty(
                        ReceivingUtils.conversionToEaches(
                            containerItem.getQuantity(),
                            containerItem.getQuantityUOM(),
                            containerItem.getVnpkQty(),
                            containerItem.getWhpkQty()));
                    receipt.setQuantityUom(containerItem.getQuantityUOM());
                    receipt.setPurchaseReferenceNumber(containerItem.getPurchaseReferenceNumber());
                    receipt.setPurchaseReferenceLineNumber(
                        containerItem.getPurchaseReferenceLineNumber());
                    receipts.add(receipt);
                  });
        });
    return receipts;
  }
}
