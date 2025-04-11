package com.walmart.move.nim.receiving.core.advice;

public enum Type {
  MESSAGE("Message"),
  REST("Rest"),
  SCHEDULER("Scheduler"),
  INTERNAL("Internal");

  private String type;

  Type(String type) {
    this.type = type;
  }

  public String getType() {
    return this.type;
  }
}
