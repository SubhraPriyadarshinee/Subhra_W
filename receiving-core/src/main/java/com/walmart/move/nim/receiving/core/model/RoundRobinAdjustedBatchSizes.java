package com.walmart.move.nim.receiving.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RoundRobinAdjustedBatchSizes {
  long adjustedOrderedBatchSize;
  long adjustedAllowedBatchSize;
}
