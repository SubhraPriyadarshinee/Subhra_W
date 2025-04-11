package com.walmart.move.nim.receiving.core.model.yms.v2;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Setter
@Getter
public class DeliveryPurchaseOrder {
  private String poNumber;
  private String unitUOM;
  private Long scheduledUnitCount;
  private Long unloadedUnitCount;
  private double unloadPercent;
}
