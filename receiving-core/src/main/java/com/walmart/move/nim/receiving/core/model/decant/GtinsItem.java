package com.walmart.move.nim.receiving.core.model.decant;

import lombok.Data;

@Data
public class GtinsItem {
  private boolean isOrderableInd;
  private GtinPhysical gtinPhysical;
}
