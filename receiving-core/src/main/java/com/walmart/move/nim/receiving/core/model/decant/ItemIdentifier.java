package com.walmart.move.nim.receiving.core.model.decant;

import lombok.Data;

@Data
public class ItemIdentifier {
  private String orderablePackGtin;
  private Long supplyItemNbr;
  private String consumableGtin;
}
