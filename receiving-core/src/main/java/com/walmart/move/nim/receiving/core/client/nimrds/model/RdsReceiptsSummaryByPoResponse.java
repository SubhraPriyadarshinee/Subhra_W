package com.walmart.move.nim.receiving.core.client.nimrds.model;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RdsReceiptsSummaryByPoResponse {

  private Long deliveryNumber;
  private Integer receivedQty;
  private List<RdsReceiptsSummaryByPo> summary;
}
