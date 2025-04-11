package com.walmart.move.nim.receiving.core.client.inventory.model;

import lombok.Data;

@Data
public class PoDetail {
  public String poNum;
  public int purchaseReferenceLineNumber;
  public int poQty;
}
