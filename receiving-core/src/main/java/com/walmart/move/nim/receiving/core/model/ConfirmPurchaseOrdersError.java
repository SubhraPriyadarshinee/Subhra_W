package com.walmart.move.nim.receiving.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class ConfirmPurchaseOrdersError {
  private String purchaseReferenceNumber;
  private String errorCode;
  private String errorMessage;

  public ConfirmPurchaseOrdersError(
      String purchaseReferenceNumber, String errorCode, String errorMessage) {
    this.purchaseReferenceNumber = purchaseReferenceNumber;
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
  }
}
