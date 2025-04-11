package com.walmart.move.nim.receiving.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeliveryLinkRequest {
  private String locationId;
  private String deliveryNumber;
  private Boolean isFilbLocation;
  private String deliveryStatus;
}
