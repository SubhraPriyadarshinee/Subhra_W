package com.walmart.move.nim.receiving.core.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ReceiptSummaryQtyByPoLine {

  private Integer lineNumber;
  private Integer itemNumber;
  private String itemDescription;
  private Integer receivedQty;
  private Integer orderQty;
  private Integer freightBillQty;
  private String vendorStockNumber;
}
