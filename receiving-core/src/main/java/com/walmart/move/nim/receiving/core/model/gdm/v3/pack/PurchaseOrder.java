package com.walmart.move.nim.receiving.core.model.gdm.v3.pack;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseOrder {
  private String poNumber;
  private String poLineNumber;
  private String orderNumber;
  private String poType;
  private String poDate;
  private int poPoLineCount;
  private String sellerId;
}
