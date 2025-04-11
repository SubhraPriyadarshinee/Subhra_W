package com.walmart.move.nim.receiving.endgame.service;

import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import java.util.Map;

/**
 * This class extends EndgameDeliveryStatusPublisher to stop publishing to hawkeye call for Nonsort
 * endgame dc.
 */
public class NonSortEndgameDeliveryStatusPublisher extends EndgameDeliveryStatusPublisher {

  /**
   * This method is responsible for publishing Delivery info including populate delivery meta info,
   * publishing event to maaS and updating unloading complete event.
   *
   * @param deliveryInfo
   * @param messageHeader
   */
  @Override
  public void publish(DeliveryInfo deliveryInfo, Map<String, Object> messageHeader) {
    DeliveryMetaData deliveryMetaData = populateDeliveryMetaInfo(deliveryInfo);
    publishMessage(deliveryInfo, messageHeader);
    updateUnloadingCompleteTs(deliveryMetaData, deliveryInfo);
  }
}
