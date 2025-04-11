package com.walmart.move.nim.receiving.core.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DeliveryDoorSummary {
  private String deliveryNumber;
  private String doorNumber;
  private boolean isDoorOccupied;
  private String trailerId;
}
