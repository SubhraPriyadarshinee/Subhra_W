package com.walmart.move.nim.receiving.mfc.model.hawkeye;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import lombok.Data;

@Data
public class StockStateExchange {
  private String currentState;
  private Double quantity;
  private String reasonCode;
  private String reasonDesc;

  public Integer getQuantity() {

    if (quantity % 1 != 0) {
      throw new ReceivingInternalException(
          ExceptionCodes.HAWK_EYE_ERROR, String.format("Quantity cannot be decimal"));
    }

    return this.quantity.intValue();
  }
}
