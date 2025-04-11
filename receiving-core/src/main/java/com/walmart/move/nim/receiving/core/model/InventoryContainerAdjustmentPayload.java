package com.walmart.move.nim.receiving.core.model;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InventoryContainerAdjustmentPayload {
  private Integer reasonCode;
  private String trackingId;
  private Map<String, String> gtins;
}
