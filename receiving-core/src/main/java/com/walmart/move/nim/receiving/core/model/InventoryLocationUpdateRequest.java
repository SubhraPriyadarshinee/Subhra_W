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
public class InventoryLocationUpdateRequest {
  List<String> trackingIds;
  DestinationLocation destinationLocation;
}
