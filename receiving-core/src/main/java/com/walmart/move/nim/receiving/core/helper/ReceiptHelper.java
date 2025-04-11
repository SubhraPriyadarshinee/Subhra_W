package com.walmart.move.nim.receiving.core.helper;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.EACHES;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ReceiptHelper {
  private static final Logger LOG = LoggerFactory.getLogger(ReceiptHelper.class);

  public List<Receipt> getReceipts(
      String userId, ContainerItem ci, Integer vtrQuantityInEaches, Long deliveryNumber) {
    Receipt receipt = createNewReceipt(userId, ci, vtrQuantityInEaches, deliveryNumber);
    final List<Receipt> receipts = new ArrayList<>(1);
    receipts.add(receipt);
    return receipts;
  }

  private Receipt createNewReceipt(
      String userId, ContainerItem ci, Integer vtrQuantityInEaches, Long deliveryNumber) {
    Receipt receipt = new Receipt();
    receipt.setDeliveryNumber(deliveryNumber);
    receipt.setPurchaseReferenceNumber(ci.getPurchaseReferenceNumber());
    receipt.setPurchaseReferenceLineNumber(ci.getPurchaseReferenceLineNumber());
    receipt.setVnpkQty(ci.getVnpkQty());
    receipt.setWhpkQty(ci.getWhpkQty());
    receipt.setEachQty(vtrQuantityInEaches);
    // if partial should be diff else if full vtr should be full -ve as current
    final int receiptQuantityInVNKP =
        ReceivingUtils.conversionToVendorPack(
            vtrQuantityInEaches, EACHES, ci.getVnpkQty(), ci.getWhpkQty());
    receipt.setQuantity(receiptQuantityInVNKP);
    receipt.setQuantityUom(VNPK);
    receipt.setCreateUserId(userId);
    receipt.setCreateTs(new Date());
    LOG.info("Constructed Receipt {}", receipt);
    return receipt;
  }
}
