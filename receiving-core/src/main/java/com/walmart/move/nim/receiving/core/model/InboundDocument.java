package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class InboundDocument {
  private String purchaseReferenceNumber;
  private Integer purchaseReferenceLineNumber;
  private String purchaseCompanyId;
  private String purchaseReferenceLegacyType;
  private String inboundChannelType;
  private String gtin;
  private String department;
  private String event;
  private Integer vendorPack;
  private Integer warehousePack;
  private Integer expectedQty;
  private String expectedQtyUOM;
  private String poDcNumber;
  private String vendorNumber;
  private String financialReportingGroup;
  private String baseDivisionCode;
  private Double vendorPackCost;
  private Double whpkSell;
  private String currency;
  private Integer overageQtyLimit;
  private String deliveryStatus;
  private Float weight; // vnpkWgtQty
  private String weightUom; // vnpkWgtUom
  private Float cube; // vnpkcbqty
  private String cubeUom; // vnpkcbuomcd
  private String description; // itemDescription1
  private String secondaryDescription; // itemDescription2
  private List<DeliveryDocumentLine> deliveryDocumentLines;
}
