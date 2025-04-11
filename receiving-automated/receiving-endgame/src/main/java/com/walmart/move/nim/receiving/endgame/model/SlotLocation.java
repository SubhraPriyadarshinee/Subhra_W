package com.walmart.move.nim.receiving.endgame.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class SlotLocation {
  private String containerTrackingId;
  private String location;
  private SlotMoveType moveType;
  private boolean moveRequired;
  private boolean holdForSale;
  private String type;
  private String code;
  private String desc;
  private String markInventoryStatus;
  private String errorCode;
  private List<String> inventoryTags;
}
