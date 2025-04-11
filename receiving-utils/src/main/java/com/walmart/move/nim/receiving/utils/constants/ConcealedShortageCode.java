package com.walmart.move.nim.receiving.utils.constants;

public enum ConcealedShortageCode {
  S29("S29", "Concealed Shortage WPM/Pallet/Case"),
  O55("55", "RCO"),
  S54("54", "RCS");

  private String code;
  private String description;

  ConcealedShortageCode(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public String getCode() {
    return code;
  }

  public String getDescription() {
    return description;
  }
}
