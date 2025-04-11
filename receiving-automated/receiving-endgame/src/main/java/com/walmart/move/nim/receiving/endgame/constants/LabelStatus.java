package com.walmart.move.nim.receiving.endgame.constants;

public enum LabelStatus {
  GENERATED("Generated"),
  SENT("Sent"),
  ATTACHED("Attached"),
  SCANNED("Scanned"),
  FAILED("Failed"),
  DELETED("Deleted");

  private String status;

  LabelStatus(String status) {
    this.status = status;
  }

  public String getStatus() {
    return this.status;
  }
}
