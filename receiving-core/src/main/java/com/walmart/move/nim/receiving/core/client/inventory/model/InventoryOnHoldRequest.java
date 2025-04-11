package com.walmart.move.nim.receiving.core.client.inventory.model;

import java.util.Date;
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
public class InventoryOnHoldRequest {
  private boolean putCompleteHierarchyOnHold;
  private List<Integer> holdReasons;
  private boolean holdAllQty;
  private String holdDirectedBy;
  private Date holdInitiatedTime;
  private String trackingId;
}
