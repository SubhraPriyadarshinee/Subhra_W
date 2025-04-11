package com.walmart.move.nim.receiving.sib.utils;

public enum EventType {
  STORE_FINALIZATION(true, false),
  STOCKING(true, false),
  CORRECTION(true, false),
  STOCKED(true, false),
  MANUAL_FINALIZATION(false, true),
  STORE_AUTO_INITIALIZATION(false, true),
  CLEANUP(false, true),
  DELIVERY_AUTO_COMPLETE(false, true),
  DELIVERY_UNLOAD_COMPLETE(false, true);

  private boolean isPublishable;
  private boolean isProcessable;

  public boolean isPublishable() {
    return isPublishable;
  }

  public boolean isProcessable() {
    return isProcessable;
  }

  EventType(boolean isPublishable, boolean isProcessable) {
    this.isPublishable = isPublishable;
    this.isProcessable = isProcessable;
  }
}
