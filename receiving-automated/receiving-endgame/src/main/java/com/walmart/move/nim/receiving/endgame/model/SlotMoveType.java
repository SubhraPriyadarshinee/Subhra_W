package com.walmart.move.nim.receiving.endgame.model;

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
