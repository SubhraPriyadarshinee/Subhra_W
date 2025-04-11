package com.walmart.move.nim.receiving.rc.contants;

public enum ItemFeedback {
  CORRECT_ITEM("CORRECT_ITEM"),
  INCORRECT_ITEM("INCORRECT_ITEM");

  private String code;

  ItemFeedback(String code) {
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
