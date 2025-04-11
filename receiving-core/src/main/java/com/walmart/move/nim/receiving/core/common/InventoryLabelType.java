package com.walmart.move.nim.receiving.core.common;

import java.util.Optional;

public enum InventoryLabelType {
  SHIPPING("SHIPPING"),
  ROUTING("ROUTING"),
  PUT("PUT"),
  XDK1("XDK1"),
  XDK2("XDK2"),
  R8000_DA_FULL_CASE("DQRL"),
  R8002_DSDC("DQRA"),
  DA_BREAK_PACK_INNER_PICK("DQRB"),
  DA_BREAK_PACK_PUT_INDUCT("IN18"),
  DA_CON_SLOTTING("LBDI"),
  DA_NON_CON_SLOTTING("LBNV"),
  DA_CON_AUTOMATION_SLOTTING("DQRL");

  private String type;

  InventoryLabelType(String type) {
    this.type = type;
  }

  public String getType() {
    return this.type;
  }

  public static Optional<InventoryLabelType> getInventoryEnumNameByValue(String labelType) {
    for (InventoryLabelType inventoryLabelType : InventoryLabelType.values()) {
      if (inventoryLabelType.getType().equals(labelType)) {
        return Optional.of(inventoryLabelType);
      }
    }
    return Optional.empty();
  }
}
