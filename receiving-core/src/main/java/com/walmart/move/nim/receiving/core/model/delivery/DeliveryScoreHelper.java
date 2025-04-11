package com.walmart.move.nim.receiving.core.model.delivery;

import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class DeliveryScoreHelper {
  private Long deliveryNumber;
  private String poNumber;
  @NotNull private Long lineNumber;
  private int receivedQuantity;
  private Integer freightBillQuantity;
}
