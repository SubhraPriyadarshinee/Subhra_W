package com.walmart.move.nim.receiving.rc.model.container;

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
@AllArgsConstructor
@NoArgsConstructor
public class RcContainerItem {
  private Integer totalPurchaseReferenceQty;
  private Integer purchaseCompanyId;
  private Integer quantity;
  private String quantityUOM;
  private Integer vnpkQty;
  private Integer whpkQty;
  private Double vnpkWgtQty;
  private String vnpkWgtUom;
  private Double vnpkcbqty;
  private String vnpkcbuomcd;
  private String itemUPC;
  private String caseUPC;
  private String gtin;
  private String description;
  private String secondaryDescription;
  private Long itemNumber;
  private String purchaseReferenceNumber;
  private Integer purchaseReferenceLineNumber;
  private String salesOrderNumber;
  private Integer salesOrderLineNumber;
  private String inboundChannelMethod;
  private String outboundChannelMethod;
  private String prePopulatedCategory;
  private String chosenCategory;
  private RcContainerAdditionalAttributes additionalAttributes;
}
