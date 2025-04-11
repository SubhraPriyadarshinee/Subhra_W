package com.walmart.move.nim.receiving.core.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DeliverySummary {
  private Integer trailerTempZonesRecorded;
  private Integer totalTrailerTempZones;
  private Integer confirmedPOsCount;
  private Integer totalPOsCount;
  private Boolean isReceiveAll;
}
