package com.walmart.move.nim.receiving.wfs.utils;

import com.walmart.move.nim.receiving.core.model.delivery.DeliveryScoreHelper;
import java.util.Comparator;

public class ScoreCompartor implements Comparator<DeliveryScoreHelper> {

  @Override
  public int compare(DeliveryScoreHelper firstDelivery, DeliveryScoreHelper secondDelivery) {
    return Integer.compare(
        firstDelivery.getFreightBillQuantity() - firstDelivery.getReceivedQuantity(),
        secondDelivery.getFreightBillQuantity() - secondDelivery.getReceivedQuantity());
  }
}
