package com.walmart.move.nim.receiving.rdc.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RdcMessageType {
  PUTAWAY("putaway");

  private final String messageType;

  public static RdcMessageType getRdcMessageType(String messageTypeText) {
    for (RdcMessageType rdcMessageType : RdcMessageType.values()) {
      if (rdcMessageType.getMessageType().equals(messageTypeText)) {
        return rdcMessageType;
      }
    }
    return null;
  }
}
