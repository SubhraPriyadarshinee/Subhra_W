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
public class AuditFlagResponse {
  private Long itemNumber;
  private Integer vendorNumber;
  private Boolean isCaseFlagged;
  private Boolean isPalletFlagged;
  private Integer palletSellableUnits;
  private Integer flaggedQty;
  private Integer orderedQty;
  private Integer vnpkRatio;
  private Integer whpkRatio;
  private String qtyUom;
  private Integer caseToAudit;
  private String vendorType;
  private String receivedQuantity;
  private Boolean isFrequentlyReceivedQuantityRequired;
}
