package com.walmart.move.nim.receiving.utils.constants;

public enum ItemTrackerCode {
  ITEM_MISSING("ITEM_MISSING"),
  NO_UPC_LABEL("NO_UPC_LABEL"),
  REPRINT_GTIN_LABEL("REPRINT_GTIN_LABEL"),
  SERIAL_NUMBER_MISMATCH("SERIAL_NUMBER_MISMATCH"),
  SERIAL_NUMBER_MATCHED("SERIAL_NUMBER_MATCHED"),
  SERIAL_NUMBER_MISSING("SERIAL_NUMBER_MISSING");

  private String code;

  ItemTrackerCode(String code) {
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
