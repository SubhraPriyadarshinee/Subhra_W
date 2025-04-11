package com.walmart.move.nim.receiving.core.model.ngr;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PackItem {
  String itemNumber;
  String gtin;
  String warehouseCaseGtin;
  String vendorCaseGtin;
  String itemDescription;
  String itemDepartment;
  String itemDivision;
  String vendorId;
  String purchaseOrderNumber;
  String reportedProductId;
  InventoryDetail inventoryDetail;
  String replenishmentCode;
  List<PackItem> childItems;
  String itemPackType;
}
