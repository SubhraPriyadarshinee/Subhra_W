package com.walmart.move.nim.receiving.core.model;

import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ReceiptSummaryQtyByPoAndDeliveryResponse {

  @NotNull private String purchaseReferenceNumber;

  @NotNull private Long receivedQty;

  public ReceiptSummaryQtyByPoAndDeliveryResponse(
      @NotNull String purchaseReferenceNumber, @NotNull Long receivedQty) {
    this.purchaseReferenceNumber = purchaseReferenceNumber;
    this.receivedQty = receivedQty;
  }
}
