package com.walmart.move.nim.receiving.core.model.decant;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PayloadItem {
  private List<GtinsItem> gtins;
  private List<SupplyItem> supplyItems;
}
