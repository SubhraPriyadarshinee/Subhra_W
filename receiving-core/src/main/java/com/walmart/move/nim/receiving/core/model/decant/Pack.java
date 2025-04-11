package com.walmart.move.nim.receiving.core.model.decant;

import lombok.Data;

@Data
public class Pack {
  private WarehousePackQuantity warehousePackQuantity;
  private WarehousePackSell warehousePackSell;
  private OrderableQuantity orderableQuantity;
}
