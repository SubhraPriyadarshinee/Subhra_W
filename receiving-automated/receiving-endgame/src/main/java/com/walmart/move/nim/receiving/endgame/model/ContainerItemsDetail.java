package com.walmart.move.nim.receiving.endgame.model;

import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class ContainerItemsDetail {
  private String caseUPC;
  private long itemNbr;
  private String itemUPC;
  private String sellerId;
  private long qty;
  private String qtyUom;
  private ItemDetails itemDetails;
  private Date rotateDate;
  private String financialReportingGroup;
  private String baseDivisionCode;
  private Long sellableUnits;
  private Integer palletTi;
  private Integer palletHi;
}
