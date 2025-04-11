package com.walmart.move.nim.receiving.core.model.symbotic;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SymPutawayItem {
  private String purchaseReferenceNumber;
  private Integer purchaseReferenceLineNumber;
  private Integer poTypeCode;
  private String poEvent;
  private Long deliveryNumber;
  private Long itemNumber;
  private Long childItemNumber;
  private String baseDivisionCode;
  private String financialReportingGroupCode;
  private Integer vendorNumber;
  private Integer quantity;
  private String quantityUOM;
  private String packagedAsUom;
  private String rotateDate;
  private Integer ti;
  private Integer hi;
  private String primeSlotId;
  private Integer deptNumber;
  private List<SymPutawayDistribution> distributions;
}
