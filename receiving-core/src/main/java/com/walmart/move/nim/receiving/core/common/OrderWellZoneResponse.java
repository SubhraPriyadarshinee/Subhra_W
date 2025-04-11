package com.walmart.move.nim.receiving.core.common;

import java.util.List;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
public class OrderWellZoneResponse {
  private List<OrderWellDistributionResponse> data;
}
