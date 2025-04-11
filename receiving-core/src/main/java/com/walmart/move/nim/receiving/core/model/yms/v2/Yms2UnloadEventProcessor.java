package com.walmart.move.nim.receiving.core.model.yms.v2;

import com.walmart.move.nim.receiving.core.common.ReceivingException;

public interface Yms2UnloadEventProcessor {

  void processYMSUnloadingEvent(Long deliveryNumber) throws ReceivingException;
}
