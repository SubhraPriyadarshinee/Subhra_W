package com.walmart.move.nim.receiving.sib.model.inventory;

import java.util.List;
import lombok.Data;

@Data
public class InventorySearchRequest {
  private Boolean populateItemMetadata;
  private String deliveryNum;
  private String containerWarehouseArea;
  private List<String> inventoryStatusList;
  private Boolean sortByOldestToLatest;
  private Boolean childContainersRequired;
  private String sortCriteria;
}
