package com.walmart.move.nim.receiving.core.model;

import java.util.Date;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class DocumentLine {
  private Integer quantity;
  private String event;
  private Integer totalPurchaseReferenceQty;
  private Integer freightBillQty;
  private String purchaseReferenceNumber;
  private Integer purchaseReferenceLineNumber;
  private String purchaseRefType;
  private String poDCNumber;
  private String quantityUOM;
  private Integer purchaseCompanyId;
  private Integer deptNumber;
  private String poDeptNumber;
  private String gtin;
  private Long itemNumber;
  private Integer vnpkQty;
  private Integer whpkQty;
  private Double vendorPackCost;
  private Double whpkSell;
  private Long maxOverageAcceptQty;
  private Long maxReceiveQty;
  private Long expectedQty;
  private String baseDivisionCode;
  private String financialReportingGroupCode;
  private Date rotateDate;
  private String vendorGS128;
  private Float vnpkWgtQty;
  private String vnpkWgtUom;
  private Float vnpkcbqty;
  private String vnpkcbuomcd;
  private String description;
  private String secondaryDescription;
  private Integer warehouseMinLifeRemainingToReceive;
  private String profiledWarehouseArea;
  private String promoBuyInd;
  private String vendorNumber;
  private String vendorNbrDeptSeq;
  private Integer palletTi;
  private Integer palletHi;
  private String inboundShipmentDocId;
  private String palletSSCC;
  private String packSSCC;
  private Integer totalReceivedQty;
  private boolean maxAllowedOverageQtyIncluded;
  private Integer maxAllowedStorageDays;
  private Date maxAllowedStorageDate;
}
