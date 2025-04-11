package com.walmart.move.nim.receiving.utils.constants;

import lombok.Getter;

@Getter
public enum DockTagType {
  FLOOR_LINE("Floorline"),
  NON_CON("PBYL"),
  NGR("NGR"),
  ATLAS_RECEIVING("ATLAS_RECEIVING");

  private String text;

  DockTagType(String text) {
    this.text = text;
  }
}
