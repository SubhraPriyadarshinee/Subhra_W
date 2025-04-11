package com.walmart.move.nim.receiving.rc.common;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.rc.contants.RcConstants;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public class RcUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(RcUtils.class);

  private RcUtils() {
    throw new ReceivingInternalException(
        ExceptionCodes.RECEIVING_INTERNAL_ERROR, "Cannot be instantiated.");
  }

  public static Long createDeliveryNumberFromSoNumber(String soNumber) {
    String deliveryNumberStr = null;
    if (!StringUtils.isEmpty(soNumber)) {
      if (soNumber.length() > RcConstants.ALLOWED_DELIVERY_NUMBER_LENGTH.intValue()) {
        deliveryNumberStr =
            soNumber.substring(0, RcConstants.ALLOWED_DELIVERY_NUMBER_LENGTH.intValue());
      } else {
        deliveryNumberStr = soNumber;
      }
      try {
        return Long.valueOf(deliveryNumberStr);
      } catch (NumberFormatException e) {
        LOGGER.error(RcConstants.EXCEPTION_HANDLER_ERROR_MESSAGE, ExceptionUtils.getStackTrace(e));
      }
    }
    return RcConstants.DEFAULT_DELIVERY_NUMBER;
  }
}
