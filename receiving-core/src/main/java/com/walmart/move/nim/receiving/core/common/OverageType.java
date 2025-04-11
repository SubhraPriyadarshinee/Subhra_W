package com.walmart.move.nim.receiving.core.common;

import java.util.Objects;

public enum OverageType {
  UNIDENTIFIED("UNIDENTIFIED"),
  DIFF_ASN_SAME_SITE("DIFF_ASN_SAME_SITE"),
  DIFF_ASN_OTHER_SITE("DIFF_ASN_OTHER_SITE"),
  UKNOWN("UKNOWN"),
  MANUAL_BILL_PALLET("MANUAL_BILL_PALLET"),
  OVERAGE_PALLET("OVERAGE_PALLET");

  private String name;

  OverageType(String type) {
    this.name = type;
  }

  public String getName() {
    return this.name;
  }

  public static OverageType defaultType() {
    return UKNOWN;
  }

  public static boolean isUnBilledPalletOverageType(OverageType overageType) {
    return Objects.nonNull(overageType)
        && (MANUAL_BILL_PALLET.equals(overageType) || OVERAGE_PALLET.equals(overageType));
  }
}
