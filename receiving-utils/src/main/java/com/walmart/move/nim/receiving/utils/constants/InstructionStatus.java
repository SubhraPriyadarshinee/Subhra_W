package com.walmart.move.nim.receiving.utils.constants;

public enum InstructionStatus {
  CREATED("Created"),
  UPDATED("Updated"),
  COMPLETED("Completed");

  private String status;

  InstructionStatus(String status) {
    this.status = status;
  }

  public String getInstructionStatus() {
    return status;
  }
}
