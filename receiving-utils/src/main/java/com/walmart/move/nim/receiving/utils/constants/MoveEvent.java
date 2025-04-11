package com.walmart.move.nim.receiving.utils.constants;

public enum MoveEvent {
  CREATE("onInitiate"),
  CANCEL("cancel"),
  REPLACE("onReplace");

  private String event;

  MoveEvent(String event) {
    this.event = event;
  }

  public String getMoveEvent() {
    return event;
  }
}
