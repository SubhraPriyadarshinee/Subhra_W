package com.walmart.move.nim.receiving.rc.contants;

public enum PackageTrackerCode {
  ON_HOLD("ON_HOLD"),
  RECEIVING_STARTED("RECEIVING_STARTED");

  private String status;

  PackageTrackerCode(String status) {
    this.status = status;
  }

  public String getStatus() {
    return status;
  }
}
