/** */
package com.walmart.move.nim.receiving.core.model;

import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Objects;
import javax.validation.constraints.NotNull;
import lombok.Getter;

/** @author a0b02ft */
@Getter
public class ReceiptSummaryEachesResponse extends ReceiptSummaryResponse {

  /**
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @param receivedQty
   */
  public ReceiptSummaryEachesResponse(
      @NotNull String purchaseReferenceNumber,
      @NotNull Integer purchaseReferenceLineNumber,
      Long palletQty,
      @NotNull Long receivedQty) {
    super(purchaseReferenceNumber, purchaseReferenceLineNumber, receivedQty);
    if (Objects.isNull(purchaseReferenceLineNumber)) {
      this.qtyUOM = ReceivingConstants.Uom.VNPK;
      this.palletQty = palletQty;
    } else this.qtyUOM = ReceivingConstants.Uom.EACHES;
  }
}
