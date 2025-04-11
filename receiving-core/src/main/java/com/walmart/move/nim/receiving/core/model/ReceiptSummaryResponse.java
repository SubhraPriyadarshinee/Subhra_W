/** */
package com.walmart.move.nim.receiving.core.model;

import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

/** @author a0b02ft JPA DTO Class will not have Setters */
@Data
@NoArgsConstructor
public class ReceiptSummaryResponse {

  protected String purchaseReferenceNumber;
  protected Integer purchaseReferenceLineNumber;
  private String problemId;
  protected Long receivedQty;
  private String inboundShipmentDocId;

  protected Long palletQty;
  protected String qtyUOM;

  public ReceiptSummaryResponse(
      @NotNull String purchaseReferenceNumber,
      @NotNull Integer purchaseReferenceLineNumber,
      @NotNull Long receivedQty) {
    super();

    this.purchaseReferenceNumber = purchaseReferenceNumber;
    this.purchaseReferenceLineNumber = purchaseReferenceLineNumber;
    this.receivedQty = receivedQty;
  }

  public ReceiptSummaryResponse(
      @NotNull String purchaseReferenceNumber, @NotNull Long receivedQty, @NotNull Long palletQty) {
    super();

    this.purchaseReferenceNumber = purchaseReferenceNumber;
    this.receivedQty = receivedQty;
    this.palletQty = palletQty;
  }

  public ReceiptSummaryResponse(@NotNull String problemId, @NotNull Long receivedQty) {
    super();

    this.problemId = problemId;
    this.receivedQty = receivedQty;
  }

  public ReceiptSummaryResponse(@NotNull Long receivedQty, @NotNull String shipmentNumber) {
    super();
    this.inboundShipmentDocId = shipmentNumber;
    this.receivedQty = receivedQty;
  }
}
