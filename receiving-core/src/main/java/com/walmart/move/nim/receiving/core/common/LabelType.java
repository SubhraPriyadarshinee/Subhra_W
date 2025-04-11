package com.walmart.move.nim.receiving.core.common;

public enum LabelType {
  TCL("TCL"),
  TPL("TPL"),
  OTHERS("OTHERS");

  private String type;

  LabelType(String type) {
    this.type = type;
  }

  public String getType() {
    return this.type;
  }
}
