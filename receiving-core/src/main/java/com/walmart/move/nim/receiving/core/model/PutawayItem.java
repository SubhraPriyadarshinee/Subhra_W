package com.walmart.move.nim.receiving.core.model;

import java.util.Date;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class PutawayItem {
  private String purchaseReferenceNumber;
  private Integer purchaseReferenceLineNumber;
  private String inboundChannelMethod;
  private String outboundChannelMethod;
  private Integer totalPurchaseReferenceQty;
  private String gtin;
  private Long itemNumber;
  private String deptNumber;
  private Integer quantity;
  private String quantityUOM;
  private Integer vnpkQty;
  private Integer whpkQty;
  private Double vendorPackCost;
  private Double whpkSell;
  private Date rotateDate;
  private String baseDivisionCode;
  private String financialReportingGroupCode;
  private Integer ti;
  private Integer hi;
  private String lotNumber;
  private String vendorNumber; // vendorNbrDeptSeq; nine digit vendorNumber
  private String packagedAsUom;
}
