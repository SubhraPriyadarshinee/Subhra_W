package com.walmart.move.nim.receiving.endgame.model;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
public class DivertRequestItem {
  private Long itemNbr;
  private Integer totalOpenQty;
  private Integer totalOrderQty;
  private Integer maxReceiveQty;
  private String baseDivisionCode;
  private String qtyUom;
  private String sellerId;
  private String itemUPC;
  private String caseUPC;
  private Boolean isConveyable;
  private Boolean isHazmat;
  private Boolean isFTS;
  private Boolean isAuditEnabled;
  private boolean isRotateDateCaptured;
  private boolean isRotateDateExpired;
  private List<String> possibleUPCs;
  private Map<String, Object> itemDetails;
}
