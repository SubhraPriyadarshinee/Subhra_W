package com.walmart.move.nim.receiving.core.common;

public enum FulfillmentMethodType {
  FLIB_CASEPACK("FLIB_CP"),
  FLIB_BREAKPACK("FLIB_BP"),
  FLIB_DSDC("FLIB_DSDC"),
  CASE_PACK_RECEIVING("RECEIVING"),
  PALLET_PULL_RECEIVING_CHILD("RECEIVING"),
  BREAK_PACK_CONVEY_PICKS_RECEIVING("RECEIVING"),
  BREAK_PACK_PUT_RECEIVING("PUT_LIGHT"),
  DA_PALLET_SLOTTING("SLOTTING"),
  DSDC("RECEIVING"),
  PALLET_PULL_RECEIVING_PARENT("PALLET_PULL"),
  DA_AUTOMATION_PALLET_SLOTTING("RECEIVING_AIB");

  private String type;

  FulfillmentMethodType(String type) {
    this.type = type;
  }

  public String getType() {
    return this.type;
  }
}
