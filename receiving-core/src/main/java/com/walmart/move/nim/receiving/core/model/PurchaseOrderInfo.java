package com.walmart.move.nim.receiving.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrderInfo {
  public String poNumber;
  public int scheduledUnitCount;
  public int unloadedUnitCount;
  public String unitUOM;
  public int unloadPercent;
}
