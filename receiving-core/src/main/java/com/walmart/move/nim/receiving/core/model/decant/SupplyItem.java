package com.walmart.move.nim.receiving.core.model.decant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SupplyItem {
  private Financials financials;
  private SiInfo siInfo;
  private ItemIdentifier itemIdentifier;
  private Pack pack;
}
