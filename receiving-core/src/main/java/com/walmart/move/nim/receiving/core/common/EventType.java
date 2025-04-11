package com.walmart.move.nim.receiving.core.common;

import java.util.Arrays;

public enum EventType {
  OFFLINE_RECEIVING,
  LABELS_GENERATED,
  LABELS_CANCELLED,
  PO_CANCELLED,
  PO_LINE_CANCELLED,
  LABELS_UPDATED,
  UNKNOWN;

  public static EventType valueOfEventType(String eventType) {
    return Arrays.stream(EventType.values())
        .filter(et -> et.name().equalsIgnoreCase(eventType))
        .findFirst()
        .orElse(UNKNOWN);
  }
}
