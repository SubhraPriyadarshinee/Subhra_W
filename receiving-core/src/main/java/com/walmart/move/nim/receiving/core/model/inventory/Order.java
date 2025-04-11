package com.walmart.move.nim.receiving.core.model.inventory;

import lombok.Data;

@Data
public class Order {
  public String orderId;
  public int itemOrderQty;
  public int destNbr;
  public String destCC;
}
