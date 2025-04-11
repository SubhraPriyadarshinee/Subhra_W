package com.walmart.move.nim.receiving.core.model;

import lombok.*;

@Data
@Getter
@Builder
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InventoryUpdateRequest {
  private String locationName;
  private String trackingId;
  private boolean processInLIUI;
}
