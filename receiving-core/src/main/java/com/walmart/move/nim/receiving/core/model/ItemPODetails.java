package com.walmart.move.nim.receiving.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemPODetails {
  private String poNum;
  private Integer purchaseReferenceLineNumber;
  private Integer poQty;
  private String destNbr;
  private String destCC;
}
