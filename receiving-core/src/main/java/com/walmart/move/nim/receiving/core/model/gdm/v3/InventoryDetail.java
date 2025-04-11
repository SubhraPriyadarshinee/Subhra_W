package com.walmart.move.nim.receiving.core.model.gdm.v3;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InventoryDetail {
  private Double reportedQuantity;
  private String reportedUom;
  private Double derivedQuantity;
  private String derivedUom;
  private Integer warehouseCaseQuantity;
  private Integer vendorCaseQuantity;
}
