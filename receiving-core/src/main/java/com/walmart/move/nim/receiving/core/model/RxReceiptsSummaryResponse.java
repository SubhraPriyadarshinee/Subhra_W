package com.walmart.move.nim.receiving.core.model;

import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class RxReceiptsSummaryResponse {

  private String purchaseReferenceNumber;
  private Integer purchaseReferenceLineNumber;
  private String ssccNumber;
  private Long receivedQty;
  private Long receivedQtyInEA;

  public RxReceiptsSummaryResponse(
      @NotNull String purchaseReferenceNumber,
      @NotNull Integer purchaseReferenceLineNumber,
      @NotNull String ssccNumber,
      @NotNull Long receivedQty,
      @NotNull Long receivedQtyInEA) {
    super();
    this.purchaseReferenceNumber = purchaseReferenceNumber;
    this.purchaseReferenceLineNumber = purchaseReferenceLineNumber;
    this.receivedQty = receivedQty;
    this.ssccNumber = ssccNumber;
    this.receivedQtyInEA = receivedQtyInEA;
  }
}
