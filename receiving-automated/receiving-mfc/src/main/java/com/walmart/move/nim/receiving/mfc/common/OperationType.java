package com.walmart.move.nim.receiving.mfc.common;

public enum OperationType {
  OVERAGE,
  // StoreInbound Phase-II , Receiving a pallet without delivery context
  OVERAGE_FROM_SAME_DELIVERY,
  MANUAL_FINALISE,
  NORMAL;
}
