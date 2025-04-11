package com.walmart.move.nim.receiving.mfc.common;

public enum AddContainerEvent {
  ADD_PACK_ITEM("ADD_PACK_ITEM"),
  ADD_PALLET("ADD_PALLET"),
  UPDATE_REPLEN_CODE_EVENT("UPDATE_REPLEN_CODE");

  private String eventType;

  AddContainerEvent(String eventType) {
    this.eventType = eventType;
  }

  public String getEventType() {
    return eventType;
  }
}
