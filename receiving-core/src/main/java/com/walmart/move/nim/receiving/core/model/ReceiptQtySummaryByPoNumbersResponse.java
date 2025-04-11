package com.walmart.move.nim.receiving.core.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ReceiptQtySummaryByPoNumbersResponse {

  private String poNumber;
  private Long receivedQty;
  private String receivedQtyUom;
}
