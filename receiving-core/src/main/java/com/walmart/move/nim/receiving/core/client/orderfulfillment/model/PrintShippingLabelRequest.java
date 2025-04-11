package com.walmart.move.nim.receiving.core.client.orderfulfillment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PrintShippingLabelRequest {
  private String routingLabelId;
  private String stagingLocation;
}
