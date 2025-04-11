package com.walmart.move.nim.receiving.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReceivingCorrectionRequest {
  private String trackingId;
  private Long itemNumber;
  private String itemUpc;
  private Integer currentQty;
  private Integer adjustBy;
  private String adjustedQuantityUOM;
  private String currentQuantityUOM;
  private Integer reasonCode;
  private String reasonDesc;
  private String financialReportingGroup;
  private String baseDivisionCode;
  private String comment;
  private String createUserId;
}
