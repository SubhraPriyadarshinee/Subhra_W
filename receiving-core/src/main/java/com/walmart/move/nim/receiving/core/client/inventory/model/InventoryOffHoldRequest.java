package com.walmart.move.nim.receiving.core.client.inventory.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryOffHoldRequest {
  private String statusPostUnHold;
  private List<Integer> holdReasons;
  private boolean removeHierarchyOnUnHold;
  private String itemStatePostHold;
  private String trackingId;
}
