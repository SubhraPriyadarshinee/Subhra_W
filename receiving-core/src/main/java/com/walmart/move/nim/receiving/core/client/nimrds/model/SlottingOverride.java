package com.walmart.move.nim.receiving.core.client.nimrds.model;

import lombok.Data;

@Data
public class SlottingOverride {
  private String slottingType;
  private String stockType;
  private Integer slotSize;
  private String xrefDoor;
  private Integer maxPallet;
  private String slot;
  private String slotRangeEnd;
}
