package com.walmart.move.nim.receiving.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class InventoryExceptionRequest {
  private String reasonCode;
  private String trackingId;
  private String comment;
  private Integer adjustBy;
  private String adjustedQuantityUOM;
  private boolean vtrFlag;
}
