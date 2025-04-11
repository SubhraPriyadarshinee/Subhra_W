package com.walmart.move.nim.receiving.rdc.utils;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.TagType;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpHeaders;

public class RdcDeliveryStatusUtils {

  public static Map<String, Object> getDeliveryStatusMessageHeaders(
      HttpHeaders headers, Long deliveryNumber) {
    Map<String, Object> messageHeaders = ReceivingUtils.getForwardablHeaderWithTenantData(headers);
    messageHeaders.put(ReceivingConstants.MESSAGE_ID_HEADER, UUID.randomUUID().toString());
    messageHeaders.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber);
    if (Objects.isNull(headers.get(ReceivingConstants.WMT_REQ_SOURCE))) {
      messageHeaders.put(ReceivingConstants.COMPONENT_ID, ReceivingConstants.ATLAS_RECEIVING);
    } else {
      messageHeaders.put(ReceivingConstants.COMPONENT_ID, ReceivingConstants.NGR_RECEIVING);
      messageHeaders.put(ReceivingConstants.WMT_REQ_SOURCE, ReceivingConstants.NGR_RECEIVING);
    }
    return messageHeaders;
  }

  public static DeliveryInfo getDockTagDeliveryInfo(Long deliveryNumber, String dockTagId) {
    DeliveryInfo deliveryInfo = new DeliveryInfo();
    deliveryInfo.setDeliveryNumber(deliveryNumber);
    deliveryInfo.setTagValue(dockTagId);
    deliveryInfo.setDeliveryStatus(DeliveryStatus.TAG_COMPLETE.name());
    deliveryInfo.setTagType(TagType.DockTag.name());
    return deliveryInfo;
  }
}
