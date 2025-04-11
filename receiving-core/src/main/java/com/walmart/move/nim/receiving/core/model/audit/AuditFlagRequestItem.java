package com.walmart.move.nim.receiving.core.model.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditFlagRequestItem {
  private Long itemNumber;
  private String qtyUom;
  private Integer whpkRatio;
  private Integer vnpkRatio;
  private Integer qty;
  private Integer orderedQty;
  private Integer vendorNumber;
  private Boolean isFrequentlyReceivedQuantityRequired;
}
