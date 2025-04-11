package com.walmart.move.nim.receiving.utils.constants;

public enum InventoryStatus {
  PICKED,
  AVAILABLE,
  ONHOLD,
  WORK_IN_PROGRESS,
  ALLOCATED,
  SOFT_DELETE,
  // Only for RDC, If label printing doesn't happen, then inventory status would be suspected in
  // order to identify the faulty container
  SUSPECTED
}
