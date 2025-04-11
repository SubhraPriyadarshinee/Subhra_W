package com.walmart.move.nim.receiving.core.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SlotDetails {

  private Integer slotSize;
  private String slot;
  private String slotRange;
  private Integer maxPallet;
  private String stockType;
  private String crossReferenceDoor;
  private String slotType;
}
