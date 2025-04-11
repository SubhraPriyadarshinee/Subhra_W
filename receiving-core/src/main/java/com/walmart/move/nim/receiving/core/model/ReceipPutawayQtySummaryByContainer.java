package com.walmart.move.nim.receiving.core.model;

import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReceipPutawayQtySummaryByContainer {

  private String purchaseReferenceNumber;
  private Integer purchaseReferenceLineNumber;
  private Long putawayQty;
  private String qtyUom;

  public ReceipPutawayQtySummaryByContainer(
      @NotNull String purchaseReferenceNumber,
      @NotNull Integer purchaseReferenceLineNumber,
      @NotNull Long putawayQty) {
    this.purchaseReferenceNumber = purchaseReferenceNumber;
    this.purchaseReferenceLineNumber = purchaseReferenceLineNumber;
    this.putawayQty = putawayQty;
    this.qtyUom = ReceivingConstants.Uom.VNPK;
  }
}
