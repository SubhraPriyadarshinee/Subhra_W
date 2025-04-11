package com.walmart.move.nim.receiving.core.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OpenQtyResult {
  private Long openQty;
  private Long maxReceiveQty;
  private Integer totalReceivedQty;
  private OpenQtyFlowType flowType;
}
