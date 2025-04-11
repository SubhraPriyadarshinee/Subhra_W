package com.walmart.move.nim.receiving.core.model.slotting;

public enum SlotMoveType {
  PUTAWAY("PUTAWAY"),
  HAUL("HAUL"),
  NONE("NONE");

  private String moveType;

  SlotMoveType(String moveType) {
    this.moveType = moveType;
  }

  public String getMoveType() {
    return moveType;
  }
}
