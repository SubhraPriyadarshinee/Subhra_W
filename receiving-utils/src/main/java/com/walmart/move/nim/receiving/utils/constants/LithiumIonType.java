package com.walmart.move.nim.receiving.utils.constants;

public enum LithiumIonType {
  METAL("METAL"),
  ION("ION");
  private String value;

  public String getValue() {
    return value;
  }

  LithiumIonType(String value) {
    this.value = value;
  }
}
