package com.walmart.move.nim.receiving.core.model.label;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DestinationData {
  private Integer buNumber;

  private String countryCode;

  private String aisle;

  private String zone;

  private String printBatch;

  private String pickBatch;
}
