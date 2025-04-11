package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InventoryItemBohQtyRequest {
  public String baseDivisionCode;
  public String financialReportingGroup;
  public String bohQtyUom;
  public String inventoryStatus;
  public List<InventoryItemList> itemList;
}
