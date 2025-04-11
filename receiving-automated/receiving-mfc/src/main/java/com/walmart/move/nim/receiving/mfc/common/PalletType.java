package com.walmart.move.nim.receiving.mfc.common;

import org.apache.commons.lang3.StringUtils;

public enum PalletType {
  MFC,
  STORE;

  public boolean equalsType(String palletType) {
    return StringUtils.isNotEmpty(palletType)
        && StringUtils.equalsIgnoreCase(this.toString(), palletType);
  }
}
