package com.walmart.move.nim.receiving.mfc.model.inventory;

public class PoDetailsItem {

  private String poNum;

  private Integer poQty;

  private Integer purchaseReferenceLineNumber;

  public String getPoNum() {
    return poNum;
  }

  public Integer getPoQty() {
    return poQty;
  }

  public Integer getPurchaseReferenceLineNumber() {
    return purchaseReferenceLineNumber;
  }

  @Override
  public String toString() {
    return "PoDetailsItem{"
        + "poNum = '"
        + poNum
        + '\''
        + ",poQty = '"
        + poQty
        + '\''
        + ",purchaseReferenceLineNumber = '"
        + purchaseReferenceLineNumber
        + '\''
        + "}";
  }
}
