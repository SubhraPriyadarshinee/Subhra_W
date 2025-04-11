package com.walmart.move.nim.receiving.mfc.model.inventory;

public class AdjustmentTO {

  private String uom;

  private Integer reasonCode;

  private Integer value;

  private String reasonDesc;

  public String getUom() {
    return uom;
  }

  public Integer getReasonCode() {
    return reasonCode;
  }

  public Integer getValue() {
    return value;
  }

  public String getReasonDesc() {
    return reasonDesc;
  }

  @Override
  public String toString() {
    return "AdjustmentTO{"
        + "uom = '"
        + uom
        + '\''
        + ",reasonCode = '"
        + reasonCode
        + '\''
        + ",value = '"
        + value
        + '\''
        + ",reasonDesc = '"
        + reasonDesc
        + '\''
        + "}";
  }
}
