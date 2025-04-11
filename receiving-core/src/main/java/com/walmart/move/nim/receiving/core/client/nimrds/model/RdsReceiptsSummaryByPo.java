package com.walmart.move.nim.receiving.core.client.nimrds.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RdsReceiptsSummaryByPo {

  private String purchaseReferenceNumber;
  private Integer receivedQty;
}
