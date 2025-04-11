package com.walmart.move.nim.receiving.core.client.orderservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LpnsInfo {

  private String trackingId;
  private String prevTrackingId;
}
