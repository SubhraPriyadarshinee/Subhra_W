package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InventoryAdjustmentList {
  private List<InventoryContainerAdjustmentPayload> adjustments;
}
