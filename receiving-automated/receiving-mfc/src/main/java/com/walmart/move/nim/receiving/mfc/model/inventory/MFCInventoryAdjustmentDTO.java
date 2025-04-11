package com.walmart.move.nim.receiving.mfc.model.inventory;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class MFCInventoryAdjustmentDTO {

  private EventObject eventObject;

  private String correlationId;

  private String id;

  private String event;

  private String user;

  private String occurredOn;

  @Override
  public String toString() {
    return "MFCInventoryAdjustment{"
        + "eventObject = '"
        + eventObject
        + '\''
        + ",correlationId = '"
        + correlationId
        + '\''
        + ",id = '"
        + id
        + '\''
        + ",event = '"
        + event
        + '\''
        + ",user = '"
        + user
        + '\''
        + ",occurredOn = '"
        + occurredOn
        + '\''
        + "}";
  }
}
