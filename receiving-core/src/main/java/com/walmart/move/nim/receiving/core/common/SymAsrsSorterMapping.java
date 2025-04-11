package com.walmart.move.nim.receiving.core.common;

public enum SymAsrsSorterMapping {
  SYM2("SYM00020"),
  SYM2_5("SYM00025"),
  SYM3("SYM00030");

  private String symLabelType;

  SymAsrsSorterMapping(String symLabelType) {
    this.symLabelType = symLabelType;
  }

  public String getSymLabelType() {
    return this.symLabelType;
  }
}
