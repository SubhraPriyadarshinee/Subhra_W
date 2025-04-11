package com.walmart.move.nim.receiving.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DestinationLocation {
  String locationName;
  Integer orgUnitId;
}
