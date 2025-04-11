package com.walmart.move.nim.receiving.acc.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserLocationRequest {
  private String locationId;
  private Long deliveryNumber;
  private Boolean isOverflowReceiving;
}
