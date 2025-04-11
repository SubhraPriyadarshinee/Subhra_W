package com.walmart.move.nim.receiving.core.model.witron;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WitronPutawayItem {
  private String purchaseReferenceNumber;
  private Integer purchaseReferenceLineNumber;
  private Long itemNumber;
  private String baseDivisionCode;
  private String financialReportingGroupCode;
  private String vendorNumber;
  private Integer quantity;
  private String quantityUOM;
  private String packagedAsUom;
  private String rotateDate;
  private Integer ti;
  private Integer hi;
  private Integer poTypeCode;
  private Integer deptNumber;
  private String lotNumber;
}
