package com.walmart.move.nim.receiving.core.model.gdm.v3;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
public class ConsolidatedDeliveryList {
  private List<ConsolidatedDelivery> data;
}
