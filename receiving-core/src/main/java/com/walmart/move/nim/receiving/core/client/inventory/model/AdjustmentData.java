package com.walmart.move.nim.receiving.core.client.inventory.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdjustmentData {
  @NonNull private String trackingId;
  private ItemDetails itemDetails;
  private int currentQty;
  private int adjustBy;
  private String uom;
  private int reasonCode;
  private String client;
}
