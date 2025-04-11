package com.walmart.move.nim.receiving.utils.constants;

public enum PoType {
  CROSSDOCK("DA"),
  CROSSU("DA"),
  CROSSMU("DA"),
  CROSSNA("DA"),
  CROSSNMA("DA"),
  MULTI("DA"),
  STAPLESTOCK("SSTK"),
  SSTKU("SSTK"),
  SSTK("SSTK"),
  SINGLE("SSTK"),
  DSDC("DSDC"),
  MIXED("MIXED"),
  RTS("RTS");

  private final String purchaseOrderType;

  public String getpoType() {
    return purchaseOrderType;
  }

  PoType(String purchaseOrderType) {
    this.purchaseOrderType = purchaseOrderType;
  }

  public static boolean contains(String po) {
    for (PoType poType : values()) if (poType.name().equals(po)) return true;
    return false;
  }
}
