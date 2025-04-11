package com.walmart.move.nim.receiving.core.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum ApplicationIdentifier {
  SSCC("00", "sscc"),
  GTIN("01", "gtin"),
  SERIAL("21", "serial"),
  EXP("17", "expiryDate"),
  LOT("10", "lot"),
  QTY("30", "qty"),
  PO("400", "po");


  @Getter private String applicationIdentifier;
  @Getter private String key;
}
