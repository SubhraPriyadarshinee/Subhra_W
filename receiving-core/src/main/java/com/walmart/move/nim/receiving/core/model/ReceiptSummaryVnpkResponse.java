/** */
package com.walmart.move.nim.receiving.core.model;

import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import javax.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/** @author a0b02ft */
@Getter
@Setter
@EqualsAndHashCode
public class ReceiptSummaryVnpkResponse extends ReceiptSummaryResponse implements Cloneable {

  private Integer vnpkQty;
  private Integer whpkQty;
  private Long orderFilledQty;
  private Long deliveryNumber;
  private Integer rcvdPackCount;
  /**
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @param receivedQty
   */
  public ReceiptSummaryVnpkResponse(
      @NotNull String purchaseReferenceNumber,
      @NotNull Integer purchaseReferenceLineNumber,
      @NotNull Long receivedQty) {
    super(purchaseReferenceNumber, purchaseReferenceLineNumber, receivedQty);
    this.qtyUOM = ReceivingConstants.Uom.VNPK;
  }

  /**
   * @param purchaseReferenceNumber
   * @param receivedQty
   */
  public ReceiptSummaryVnpkResponse(
      @NotNull String purchaseReferenceNumber, @NotNull Long receivedQty) {
    this.purchaseReferenceNumber = purchaseReferenceNumber;
    this.receivedQty = receivedQty;
    this.qtyUOM = ReceivingConstants.Uom.VNPK;
  }

  public ReceiptSummaryVnpkResponse(
      @NotNull String purchaseReferenceNumber,
      @NotNull Integer purchaseReferenceLineNumber,
      @NotNull Integer vnpkQty,
      @NotNull Integer whpkQty,
      @NotNull String qtyUOM,
      @NotNull Long receivedQty) {
    super(purchaseReferenceNumber, purchaseReferenceLineNumber, receivedQty);
    this.qtyUOM = qtyUOM;
    this.vnpkQty = vnpkQty;
    this.whpkQty = whpkQty;
  }

  public ReceiptSummaryVnpkResponse(
      @NotNull String purchaseReferenceNumber,
      @NotNull Integer purchaseReferenceLineNumber,
      @NotNull String qtyUOM,
      @NotNull Long receivedQty,
      @NotNull Long orderFilledQty) {
    super(purchaseReferenceNumber, purchaseReferenceLineNumber, receivedQty);
    this.qtyUOM = qtyUOM;
    this.orderFilledQty = orderFilledQty;
  }

  public ReceiptSummaryVnpkResponse(
      @NotNull Long deliveryNumber, @NotNull Long receivedQty, @NotNull String qtyUOM) {
    this.qtyUOM = qtyUOM;
    this.deliveryNumber = deliveryNumber;
    this.receivedQty = receivedQty;
  }

  public ReceiptSummaryVnpkResponse(
      @NotNull String purchaseReferenceNumber, @NotNull Long receivedQty, @NotNull String qtyUOM) {
    this.qtyUOM = qtyUOM;
    this.purchaseReferenceNumber = purchaseReferenceNumber;
    this.receivedQty = receivedQty;
  }

  public ReceiptSummaryVnpkResponse(
      @NotNull String purchaseReferenceNumber, @NotNull Integer rcvdPackCount) {
    this.purchaseReferenceNumber = purchaseReferenceNumber;
    this.rcvdPackCount = rcvdPackCount;
  }
}
