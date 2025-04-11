package com.walmart.move.nim.receiving.rdc.model.symbotic;

public enum SymInventoryStatus {
  AVAILABLE("Available"),
  HOLD("Hold");

  private final String status;

  public String getStatus() {
    return this.status;
  }

  SymInventoryStatus(String status) {
    this.status = status;
  }
}
