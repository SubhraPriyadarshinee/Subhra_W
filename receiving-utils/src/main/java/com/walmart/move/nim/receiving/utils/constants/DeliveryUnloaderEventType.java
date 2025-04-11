package com.walmart.move.nim.receiving.utils.constants;

public enum DeliveryUnloaderEventType {
  UNLOAD_START,
  UNLOAD_STOP,
  UNLOAD_OPEN,
  UNLOAD_WORKING,
  UNLOADER_WORKING;

  public static DeliveryUnloaderEventType getDeliveryEventType(String deliveryEventType) {
    return valueOf(deliveryEventType);
  }
}
