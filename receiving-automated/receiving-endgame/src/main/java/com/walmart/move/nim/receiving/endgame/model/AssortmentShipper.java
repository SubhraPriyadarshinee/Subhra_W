package com.walmart.move.nim.receiving.endgame.model;

import lombok.Getter;

@Getter
public class AssortmentShipper {

  private Attributes attributes;
  private RelatedItem relatedItem;

  @Getter
  public static class Attributes {

    private String consumableGtin;
    private Long itemNbr;
    private String orderablePackGtin;
    private String quantity;
    private Double warehousePackCostAmt;
    private String warehousePackGtin;
  }

  @Getter
  public static class RelatedItem {
    private Attributes attributes;
  }
}
