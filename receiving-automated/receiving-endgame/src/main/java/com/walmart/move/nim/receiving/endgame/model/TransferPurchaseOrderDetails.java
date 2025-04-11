package com.walmart.move.nim.receiving.endgame.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class TransferPurchaseOrderDetails {
  private String purchaseReferenceNumber;
  private Integer purchaseReferenceLineNumber;
  private Long itemNumber;
  private String gtin;
  private Integer quantity;
  private String quantityUOM;
  private String sellerId;
}
