package com.walmart.move.nim.receiving.core.mock.data;

import com.walmart.move.nim.receiving.core.entity.DeliveryItemOverride;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.HashMap;
import java.util.Map;

public class MockDeliveryItemOverride {
  public static DeliveryItemOverride getDeliveryItemOverride() {
    DeliveryItemOverride deliveryItemOverride = new DeliveryItemOverride();
    deliveryItemOverride.setDeliveryNumber(Long.parseLong("21119003"));
    deliveryItemOverride.setItemNumber(Long.parseLong("550129241"));
    Map<String, String> itemMiscInfo = new HashMap<>();
    itemMiscInfo.put(ReceivingConstants.PACK_TYPE_CODE, "B");
    itemMiscInfo.put(ReceivingConstants.TEMPORARY_HANDLING_METHOD_CODE, "C");
    itemMiscInfo.put(ReceivingConstants.TEMPORARY_PACK_TYPE_CODE, "N");
    itemMiscInfo.put(ReceivingConstants.HANDLING_CODE, "B");
    deliveryItemOverride.setItemMiscInfo(itemMiscInfo);
    return deliveryItemOverride;
  }
}
