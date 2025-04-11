package com.walmart.move.nim.receiving.core.event.processor.unload;

import com.walmart.move.nim.receiving.core.model.DeliveryInfo;

public interface DeliveryUnloadingProcessor {
  void doProcess(DeliveryInfo deliveryInfo);
}
