package com.walmart.move.nim.receiving.core.model.symbotic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SymPutawayDistribution {
  private String storeId;
  private String zone;
  private String aisle;
  private Integer allocQty;
  private String orderId;
}
