package com.walmart.move.nim.receiving.core.mock.data;

import com.walmart.move.nim.receiving.core.entity.DeliveryItemOverride;

public class MockTempTiHi {

  public static DeliveryItemOverride getDeliveryItemOverride() {
    DeliveryItemOverride deliveryItemOverride = new DeliveryItemOverride();
    deliveryItemOverride.setDeliveryNumber(Long.parseLong("21119003"));
    deliveryItemOverride.setItemNumber(Long.parseLong("550129241"));
    deliveryItemOverride.setTempPalletTi(4);
    deliveryItemOverride.setTempPalletHi(2);
    deliveryItemOverride.setVersion(1);

    return deliveryItemOverride;
  }
}
