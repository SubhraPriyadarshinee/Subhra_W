package com.walmart.move.nim.receiving.core.common;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
public class OrderWellDistributionResponse {
  private OWMfcDistribution mfcDistribution;
  private OWStoreDistribution storeDistribution;
}
