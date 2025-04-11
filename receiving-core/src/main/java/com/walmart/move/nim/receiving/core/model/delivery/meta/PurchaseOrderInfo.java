package com.walmart.move.nim.receiving.core.model.delivery.meta;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class PurchaseOrderInfo {
  private final Long deliveryNumber;
  private final String purchaseReferenceNumber;
  private final Integer purchaseReferenceLineNumber;
  private final String possibleUPC;

  @Override
  public String toString() {
    return "PurchaseOrderInfo{"
        + "deliveryNumber="
        + deliveryNumber
        + ", purchaseReferenceNumber='"
        + purchaseReferenceNumber
        + '\''
        + ", purchaseReferenceLineNumber="
        + purchaseReferenceLineNumber
        + '}';
  }
}
