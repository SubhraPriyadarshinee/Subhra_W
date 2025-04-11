package com.walmart.move.nim.receiving.core.model;

import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class ContainerItemRequest {
  private String purchaseReferenceNumber;
  private Integer purchaseReferenceLineNumber;
  private String inboundChannelMethod;
  private String outboundChannelMethod;
  private Integer totalPurchaseReferenceQty;
  private Integer purchaseCompanyId;
  private Integer deptNumber;
  private String poDeptNumber;
  private Long itemNumber;
  private String gtin;
  private Integer quantity;
  private String quantityUom;
  private Integer vnpkQty;
  private Integer whpkQty;
  private Double vendorPackCost;
  private Double whpkSell;
  private String baseDivisionCode;
  private String financialReportingGroupCode;
  private Date rotateDate;
  private Float vnpkWgtQty;
  private String vnpkWgtQtyUom;
  private Float vnpkCubeQty;
  private String vnpkCubeQtyUom;
  private String description;
  private String secondaryDescription;
  private Integer vendorNumber;
  private String lotNumber;
  private Integer actualTi;
  private Integer actualHi;
  private String packagedAsUom;
  private List<Distribution> distributions;
  private String promoBuyInd;
}
