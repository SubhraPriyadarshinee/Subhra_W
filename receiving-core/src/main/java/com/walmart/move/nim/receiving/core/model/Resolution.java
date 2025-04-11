package com.walmart.move.nim.receiving.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Resolution {
  private String id;
  private String provider;
  private Integer quantity;
  private Integer acceptedQuantity;
  private Integer rejectedQuantity;
  private Integer remainingQty;
  private String type;
  private String resolutionPoNbr;
  private Integer resolutionPoLineNbr;
  private Integer correctUPCNumber;
  private String state;
  private Integer totalResultItemCost;
  private Integer totalRetailCostAmt;
  private String wmra;
  private Long receivedQuantity;
}
