package com.walmart.move.nim.receiving.core.advice;

public enum AppComponent {
  ENDGAME("Endgame"),
  ACC("ACC"),
  WITRON("Witron"),
  CORE("Core"),
  MCC("MCC"),
  RC("RC"),
  RDC("RDC"),
  RDS("RDS"),
  FIXTURE("FIXTURE");

  private String component;

  AppComponent(String component) {
    this.component = component;
  }

  public String getComponent() {
    return component;
  }
}
