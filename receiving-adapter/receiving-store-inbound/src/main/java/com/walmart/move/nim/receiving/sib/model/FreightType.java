package com.walmart.move.nim.receiving.sib.model;

public enum FreightType {
  MEAT_PRODUCE,
  NHM,
  SC,
  OVERAGE;

  public static String getFreightType(FreightType freightType) {
    return freightType.name();
  }

  public static FreightType getFreightType(String freightType) {
    return valueOf(freightType);
  }
}
