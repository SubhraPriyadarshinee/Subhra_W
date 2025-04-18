package com.walmart.move.nim.receiving.core.message.common;

public enum ItemUpdateEventType {
  CONVEYABLE_TO_NONCON_GLOBAL,
  NONCON_TO_CONVEYABLE_GLOBAL,
  SSTKU_TO_CROSSU_DELIVERY,
  CROSSU_TO_SSTKU_DELIVERY,
  UPC_CATALOG_GLOBAL,
  UPC_CATALOG_DELIVERY,
  UNDO_CATALOG_GLOBAL,
  UNDO_CATALOG_DELIVERY,
  HANDLING_CODE_UPDATE,
  CATALOG_GTIN_UPDATE,
  CHANNEL_FLIP
}
