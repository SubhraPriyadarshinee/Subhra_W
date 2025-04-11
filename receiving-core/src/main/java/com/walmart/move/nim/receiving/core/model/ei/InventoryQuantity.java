package com.walmart.move.nim.receiving.core.model.ei;

import lombok.Data;

@Data
public class InventoryQuantity {

  private String invTypeInd;
  private String vnpkWgtFormatCode;
  private Integer avgCaseWgtQty;
  private String uom;
  private Integer value;
}
