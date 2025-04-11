package com.walmart.move.nim.receiving.core.model.decant;

import lombok.Data;

@Data
public class WeightItem {
  private float amount;
  private String uom;
  private String formatTypeCode;
}
