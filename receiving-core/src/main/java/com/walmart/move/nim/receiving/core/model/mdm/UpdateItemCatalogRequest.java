package com.walmart.move.nim.receiving.core.model.mdm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateItemCatalogRequest {
  private Long ItemNumber;
  private String baseDivisionCode;
  private String financialReportingGroupCode;
  private SupplyItem supplyItem;
}
