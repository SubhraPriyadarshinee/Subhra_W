package com.walmart.move.nim.receiving.core.model.ngr;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InventoryDetail {
  String reportedQuantity;
  String reportedQuantityUom;
  String expectedQuantity;
  String receivedQuantity;
  String rejectedQuantity;
  String errorQuantity;
  String expectedQuantityUom;
  String receivedQuantityUom;
  String rejectedQuantityUom;
  String errorQuantityUom;
  String warehouseCaseQuantity;
  String vendorCaseQuantity;
  BigDecimal receivedPhysicalQuantity;
  String receivedPhysicalQuantityUom;
  BigDecimal receivedInventoryQuantity;
  String receivedInventoryUom;
  RejectionInfo rejectionInfo;
}
