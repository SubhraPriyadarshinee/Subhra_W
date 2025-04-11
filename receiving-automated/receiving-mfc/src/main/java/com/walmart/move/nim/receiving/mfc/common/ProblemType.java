package com.walmart.move.nim.receiving.mfc.common;

import org.apache.commons.lang3.StringUtils;

public enum ProblemType {
  OVERAGE("Overage"),
  SHORTAGE("Shortage");

  private String name;

  ProblemType(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public ProblemType getProblemType(String name) {
    for (ProblemType problemType : ProblemType.values()) {
      if (StringUtils.equalsIgnoreCase(name, problemType.getName())) {
        return problemType;
      }
    }
    return null;
  }
}
