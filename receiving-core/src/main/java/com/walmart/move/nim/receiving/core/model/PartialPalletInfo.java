package com.walmart.move.nim.receiving.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartialPalletInfo {
  private int partialPalletQuantity;
  private String slotSize;
}
