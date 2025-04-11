package com.walmart.move.nim.receiving.mfc.model.gdm;

import org.apache.commons.lang3.StringUtils;

public enum PackType {
  MIXED_PACK("STORE_MFC"),
  MFC("MFC"),
  STORE("STORE"),
  UNKNOWN("UNKNOWN");

  private String packType;

  PackType(String packType) {
    this.packType = packType;
  }

  public String getPackType() {
    return packType;
  }

  public static PackType getPackType(String packType) {

    for (PackType _packType : values()) {
      if (!StringUtils.equalsIgnoreCase(_packType.packType, packType)) {
        continue;
      }
      return _packType;
    }
    return PackType.UNKNOWN;
  }
}
