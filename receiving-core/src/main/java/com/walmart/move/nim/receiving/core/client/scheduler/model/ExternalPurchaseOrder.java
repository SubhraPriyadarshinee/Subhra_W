package com.walmart.move.nim.receiving.core.client.scheduler.model;

import lombok.*;

@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
public class ExternalPurchaseOrder {
  private String poNumber;
  private int poCaseQty;
}
