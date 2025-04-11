package com.walmart.move.nim.receiving.core.model.decant;

import java.util.List;
import lombok.Data;

@Data
public class GtinPhysical {
  private List<WeightItem> weight;
  private List<CubeItem> cube;
}
