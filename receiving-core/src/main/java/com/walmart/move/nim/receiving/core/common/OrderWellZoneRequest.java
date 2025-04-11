package com.walmart.move.nim.receiving.core.common;

import java.util.List;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
public class OrderWellZoneRequest {
  private List<OrderWellStoreDistribution> data;
}
