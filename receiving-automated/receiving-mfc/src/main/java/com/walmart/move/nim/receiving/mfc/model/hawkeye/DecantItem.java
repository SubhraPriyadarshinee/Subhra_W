package com.walmart.move.nim.receiving.mfc.model.hawkeye;

import java.util.List;
import lombok.Data;

@Data
public class DecantItem {
  private String productId;
  private String gtin;
  private String quantityUom;
  private String previousState;
  /*DSD Flow*/
  private Long itemTypeCode;
  private Long replenishSubTypeCode;
  private List<StockStateExchange> stockStateChange;
}
