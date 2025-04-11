package com.walmart.move.nim.receiving.core.model;

import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Receipt summary qty by problem id
 *
 * @author g0k0072
 */
@Getter
@Setter
@ToString
public class ReceiptSummaryQtyByProblemIdResponse extends ReceiptSummaryResponse {

  /**
   * @param problemId Problem Tag Id
   * @param receivedQty
   */
  public ReceiptSummaryQtyByProblemIdResponse(
      @NotNull String problemId, @NotNull Long receivedQty) {
    super(problemId, receivedQty);
    this.qtyUOM = ReceivingConstants.Uom.VNPK;
  }
}
