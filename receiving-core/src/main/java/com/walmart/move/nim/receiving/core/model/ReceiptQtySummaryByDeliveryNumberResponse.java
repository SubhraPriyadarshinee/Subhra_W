package com.walmart.move.nim.receiving.core.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ReceiptQtySummaryByDeliveryNumberResponse {

  private Long deliveryNumber;
  private Long receivedQty;
  private String receivedQtyUom;
}
