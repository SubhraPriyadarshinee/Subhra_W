package com.walmart.move.nim.receiving.endgame.constants;

public enum LabelGenMode {
  MOBILE("Mobile"),
  WEB("Web"),
  AUTOMATED("Automated");

  private String mode;

  LabelGenMode(String mode) {
    this.mode = mode;
  }

  public String getMode() {
    return this.mode;
  }
}
