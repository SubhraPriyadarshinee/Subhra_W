/** */
package com.walmart.move.nim.receiving.core.model;

import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** @author a0b02ft */
@Getter
@Setter
@ToString
public class ReceiptSummaryQtyByPoAndPoLineResponse extends ReceiptSummaryResponse {

  /**
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @param receivedQty
   */
  public ReceiptSummaryQtyByPoAndPoLineResponse(
      @NotNull String purchaseReferenceNumber,
      @NotNull Integer purchaseReferenceLineNumber,
      @NotNull Long receivedQty) {
    super(purchaseReferenceNumber, purchaseReferenceLineNumber, receivedQty);
    this.qtyUOM = ReceivingConstants.Uom.VNPK;
  }
}
