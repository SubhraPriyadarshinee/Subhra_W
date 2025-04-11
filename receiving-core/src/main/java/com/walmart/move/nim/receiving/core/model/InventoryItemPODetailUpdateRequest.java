package com.walmart.move.nim.receiving.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InventoryItemPODetailUpdateRequest {
  private SearchCriteria searchCriteria;
  private UpdateAttributes updateAttributes;
}
