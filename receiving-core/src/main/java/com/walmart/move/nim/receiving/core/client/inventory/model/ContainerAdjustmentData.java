package com.walmart.move.nim.receiving.core.client.inventory.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContainerAdjustmentData {
  private String vtrFlag;
  private String comment;
  private int reasonCode;
  private String reasonDesc;
  private String trackingId;
}
