package com.walmart.move.nim.receiving.core.model;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Content implements Serializable {
  private String gtin;
  private Long itemNbr;
  private String baseDivisionCode;
  private String financialReportingGroup;
  private String purchaseCompanyId;
  private String purchaseReferenceNumber;
  private Integer purchaseReferenceLineNumber;
  private String poDcNumber;
  private String purchaseRefType;
  private Integer qty;
  private String qtyUom;
  private Integer vendorPack;
  private Integer warehousePack;
  private Integer openQty;
  private Integer totalOrderQty;
  private Integer palletTie;
  private Integer palletHigh;
  private Integer maxReceiveQty;
  private Float warehousePackSell;
  private Float vendorPackCost;
  private String currency;
  private Integer deptNumber;
  private String color;
  private String size;
  private String description;
  private String secondaryDescription;
  private Boolean isConveyable;
  private Boolean onConveyor;
  private Boolean isHazmat;
  private String purchaseReferenceLegacyType;
  private String vendorNumber;
  private String event;
  private String profiledWarehouseArea;
  private String warehouseAreaCode;
  private String warehouseAreaCodeValue;
  private List<Distribution> distributions;
  private Integer freightBillQty;
  private Boolean isManualReceivingEnabled;
  private String poDcCountry;
  private String serial;
  private String lot;
  private String caseUPC;
  private String vendorUPC;
  private String rotateDate;
  private String sellerId;
  private String sellerType;
  private Integer receiveQty;
  private String receivingUnit;
  private Boolean importInd;
  private String recommendedFulfillmentType;
  private ItemData additionalInfo;
  private String ssccPackId;
  private Boolean isAuditRequired;
  private Timestamp receivingScanTimeStamp;
  private String shelfLPN;
  private String reReceivingShipmentNumber;
}
