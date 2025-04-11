package com.walmart.move.nim.receiving.mfc.message.publisher;

import com.walmart.move.nim.receiving.mfc.model.ngr.NGRShipment;

public interface NGRShipmentPublisher {
  public void publish(NGRShipment message);
}
