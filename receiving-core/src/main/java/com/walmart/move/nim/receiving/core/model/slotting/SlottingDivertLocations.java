package com.walmart.move.nim.receiving.core.model.slotting;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SlottingDivertLocations {
  private String type;
  private String location;
  private long locationSize;
  private long itemNbr;

  private String primeLocation;
  private String containerTrackingId;
  // Error Response Details
  private String code;
  private String desc;
  // Symbotic
  private String slotType;
  private String asrsAlignment;
  private SlotMoveType moveType;
  private boolean moveRequired;
  private boolean holdForSale;
  private String markInventoryStatus;
  private String errorCode;
  private String containerType;
}
