package com.walmart.move.nim.receiving.utils.constants;

public enum DeliveryReasonCodeState {
  DOOR_OPEN("Door Open"),
  WORKING("Working"),
  PENDING_PROBLEM("Pending Problem Tag"),
  READY_TO_RECEIVE("Ready To Receive"),
  UNFINALIZED("Unfinalized"),
  DELIVERY_REOPENED("Delivery Reopened"),
  PENDING_DOCK_TAG("Pending Dock Tag"),
  PENDING_AUDIT_TAG("PENDING_AUDIT_TAG");

  private String label;

  DeliveryReasonCodeState(String label) {
    this.label = label;
  }

  public String getLabel() {
    return this.label;
  }
}
