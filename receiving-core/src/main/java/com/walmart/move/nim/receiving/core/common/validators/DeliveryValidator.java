/** */
package com.walmart.move.nim.receiving.core.common.validators;

import com.walmart.move.nim.receiving.core.advice.FeatureFlag;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import org.springframework.stereotype.Component;

/** @author m0g028p */
@Component
public class DeliveryValidator {

  @FeatureFlag(value = "deliveryStatusValidatorFeature")
  public void validateDeliveryStatus(DeliveryDocument deliveryDocument) {

    if (!(DeliveryStatus.OPN.equals(deliveryDocument.getDeliveryStatus())
        || DeliveryStatus.WRK.equals(deliveryDocument.getDeliveryStatus()))) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_DELIVERY_STATUS,
          String.format(
              ExceptionDescriptionConstants.VALID_DELIVERY_STATUS_MESSAGE,
              DeliveryStatus.OPN,
              DeliveryStatus.WRK));
    }
  }
}
