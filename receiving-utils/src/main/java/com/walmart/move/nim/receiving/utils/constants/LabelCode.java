package com.walmart.move.nim.receiving.utils.constants;

public enum LabelCode {
  UN3090("3090"),
  UN3091("3091"),
  UN3480("3480"),
  UN3481("3481");
  private String value;

  public String getValue() {
    return value;
  }

  LabelCode(String value) {
    this.value = value;
  }
}
