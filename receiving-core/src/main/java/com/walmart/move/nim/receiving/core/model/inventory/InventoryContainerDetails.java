package com.walmart.move.nim.receiving.core.model.inventory;

import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class InventoryContainerDetails {
  Integer inventoryQty;
  String containerStatus;
  Integer destinationLocationId;
  Integer allocatedQty;
  String locationName;

  public InventoryContainerDetails(
      Integer inventoryQty,
      String containerStatus,
      Integer destinationLocationId,
      Integer allocatedQty) {
    this.inventoryQty = inventoryQty;
    this.containerStatus = containerStatus;
    this.destinationLocationId = destinationLocationId;
    this.allocatedQty = allocatedQty;
  }
}
