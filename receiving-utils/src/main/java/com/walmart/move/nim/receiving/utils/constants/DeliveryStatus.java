package com.walmart.move.nim.receiving.utils.constants;

/** ENUM to represent delivery statuses */
public enum DeliveryStatus {
  SCH,
  ARV,
  OPN,
  OPEN,
  WRK,
  WORKING,
  TRAILER_CLOSE,
  CLOSE,
  COMPLETE,
  PNDFNL,
  PENDING_FINALIZED,
  PNDPT,
  REO,
  FNL,
  FINALIZED,
  UNLOADING_COMPLETE,
  TAG_COMPLETE,
  SYS_REO,
  SHIPMENT_ADDED;

  public static DeliveryStatus getDeliveryStatus(String deliveryStatus) {
    return valueOf(deliveryStatus);
  }
}
