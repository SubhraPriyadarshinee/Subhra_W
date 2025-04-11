package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.model.ReceivingCountSummary;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.springframework.stereotype.Component;

/**
 * Calculates OSDR given receiving counts and freight bill quantity
 *
 * @author v0k00fe
 */
@Component
public class OSDRCalculator {

  public void calculate(ReceivingCountSummary receivingCountSummary) {

    int totalReceiveQty = 0;
    totalReceiveQty += receivingCountSummary.getReceiveQty();
    totalReceiveQty += receivingCountSummary.getProblemQty();
    totalReceiveQty += receivingCountSummary.getDamageQty();
    totalReceiveQty += receivingCountSummary.getRejectedQty();

    receivingCountSummary.setTotalReceiveQty(totalReceiveQty);

    int totalFBQty = receivingCountSummary.getTotalFBQty();

    int resultQty = totalFBQty - totalReceiveQty;
    if (resultQty < 0) {
      receivingCountSummary.setOverageQty(resultQty * -1);
      receivingCountSummary.setOverageQtyUOM(ReceivingConstants.Uom.VNPK);
    } else {
      receivingCountSummary.setShortageQty(resultQty);
      receivingCountSummary.setShortageQtyUOM(ReceivingConstants.Uom.VNPK);
    }
  }
}
