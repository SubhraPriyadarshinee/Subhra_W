package com.walmart.move.nim.receiving.mfc.common;

import org.apache.commons.lang3.StringUtils;

public enum ProblemResolutionType {
  UNRESOLVED("UNRESOLVED"),
  RESOLVED("RESOLVED");

  private String name;

  ProblemResolutionType(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public ProblemResolutionType getProblemType(String name) {
    for (ProblemResolutionType problemResolutionType : ProblemResolutionType.values()) {
      if (StringUtils.equalsIgnoreCase(name, problemResolutionType.getName())) {
        return problemResolutionType;
      }
    }
    return null;
  }
}
