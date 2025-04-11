package com.walmart.move.nim.receiving.core.client.hawkeye.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class HawkeyeGetLpnsRequest {
  private String deliveryNumber;
  private Integer itemNumber;
  private Integer quantity;
  private Integer storeNumber;
}
