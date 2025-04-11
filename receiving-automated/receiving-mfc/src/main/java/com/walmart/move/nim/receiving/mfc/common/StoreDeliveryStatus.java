package com.walmart.move.nim.receiving.mfc.common;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

public enum StoreDeliveryStatus {
  SCH("SCH", 100),
  ARV("ARV", 200),
  OPEN("OPEN", 300),
  WORKING("WRK", 400),
  UNLOADING_COMPLETE("UNLOADING_COMPLETE", 500),
  FINALIZED("COMPLETE", 600);

  private String status;
  private int order;

  StoreDeliveryStatus(String status, int order) {
    this.status = status;
    this.order = order;
  }

  public int getOrder() {
    return order;
  }

  public static StoreDeliveryStatus getDeliveryStatus(String status) {
    for (StoreDeliveryStatus deliveryStatus : values()) {
      if (StringUtils.equalsIgnoreCase(deliveryStatus.status, status)) {
        return deliveryStatus;
      }
    }
    throw new ReceivingInternalException(
        ExceptionCodes.INVALID_DELIVERY_STATUS,
        String.format(MFCConstant.DELIVERY_STATUS_NOT_SUPPORTED, status));
  }

  public static StoreDeliveryStatus getDeliveryStatus(DeliveryStatus status) {
    if (Objects.nonNull(status)) {
      for (StoreDeliveryStatus deliveryStatus : values()) {
        if (StringUtils.equalsIgnoreCase(deliveryStatus.status, status.name())) {
          return deliveryStatus;
        }
      }
    }
    throw new ReceivingInternalException(
        ExceptionCodes.INVALID_DELIVERY_STATUS,
        String.format(MFCConstant.DELIVERY_STATUS_NOT_SUPPORTED, status.name()));
  }

  /** allow delivery update only if order of new Status > current status */
  public static boolean isValidDeliveryStatusForUpdate(
      StoreDeliveryStatus currentStatus, StoreDeliveryStatus newStatus) {
    return newStatus.getOrder() > currentStatus.getOrder();
  }
}
