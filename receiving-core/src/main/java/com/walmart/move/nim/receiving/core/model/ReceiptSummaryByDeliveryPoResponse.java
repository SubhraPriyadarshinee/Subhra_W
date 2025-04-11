package com.walmart.move.nim.receiving.core.model;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class ReceiptSummaryByDeliveryPoResponse extends ReceiptSummaryResponse {
  private final Long deliveryNumber;

  public ReceiptSummaryByDeliveryPoResponse(
      Long deliveryNumber, String purchaseReferenceNumber, Long receivedQty) {
    this.deliveryNumber = deliveryNumber;
    this.purchaseReferenceNumber = purchaseReferenceNumber;
    this.receivedQty = receivedQty;
  }
}
