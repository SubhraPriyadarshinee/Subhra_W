package com.walmart.move.nim.receiving.mfc.model.problem.lq;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class Item {
  private String itemNumber;
  private String itemCost;
  private String departmentNumber;
  private String palletNumber;
  private String invoiceLineNumber;
  private String qtyUOM;
  private String itemUpc;
  private String qty;
  private String invoiceNumber;
  private String itemDescription;
  private String costCurrency;
  private String replenishmentCode;
  private String packNumber;
}
