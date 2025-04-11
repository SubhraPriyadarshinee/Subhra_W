package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InventoryPutawayConfirmationRequest {
  private String trackingId;
  private String status;
  private Integer quantity;
  private String quantityUOM;
  private List errorDetails;
  private String destinationLocationId;
  private Long itemNumber;
  private boolean forceComplete;
}
