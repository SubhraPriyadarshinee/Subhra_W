package com.walmart.move.nim.receiving.core.model.itemupdate;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class ItemUpdateGtinAttribute {
  private String gtin;
  private boolean isCataloguedItem;
}
