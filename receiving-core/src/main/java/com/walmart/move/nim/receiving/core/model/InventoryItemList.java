package com.walmart.move.nim.receiving.core.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InventoryItemList {
  public Long itemNumber;
  public String inventoryAggLevel;
}
