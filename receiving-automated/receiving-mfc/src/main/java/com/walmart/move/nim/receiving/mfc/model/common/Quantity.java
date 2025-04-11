package com.walmart.move.nim.receiving.mfc.model.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class Quantity {
  private Long value;
  private String uom;
  private QuantityType type;
}
