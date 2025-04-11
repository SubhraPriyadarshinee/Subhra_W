package com.walmart.move.nim.receiving.mfc.model.mixedpallet;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PalletItem {
  private String gtin;
  private String quantityUom;
  private String previousState;
  private String invoiceNumber;
  private List<StockQuantityChange> stockStateChange;
}
