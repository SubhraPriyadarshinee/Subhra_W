package com.walmart.move.nim.receiving.endgame.constants;

public enum DivertStatus {
  PALLET_BUILD("PALLET_BUILD"),
  DECANT("DECANT"),
  DECANT2("DECANT2"),
  IB_BUFFER("IB_BUFFER"),
  FTS_BUFFER("FTS_BUFFER"),
  QA("QA");

  private String status;

  DivertStatus(String status) {
    this.status = status;
  }

  public String getStatus() {
    return this.status;
  }
}
