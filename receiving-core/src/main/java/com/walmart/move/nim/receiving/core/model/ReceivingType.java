package com.walmart.move.nim.receiving.core.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum ReceivingType {
  SSCC("SSCC"),
  UPC("UPC"),
  LPN("LPN"),
  GS1("GS1");
  @Getter private String receivingType;
}
