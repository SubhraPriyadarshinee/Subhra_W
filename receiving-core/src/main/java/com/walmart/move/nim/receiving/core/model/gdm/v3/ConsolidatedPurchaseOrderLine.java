package com.walmart.move.nim.receiving.core.model.gdm.v3;

import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ConsolidatedPurchaseOrderLine {
  @NotNull private Integer poLineNumber;
  private Integer freightBillQty;
  private QuantityDetail received;
}
