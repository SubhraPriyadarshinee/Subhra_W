package com.walmart.move.nim.receiving.core.model.fixit;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
public class PurchaseOrderLine {
  private Integer polineorderqty;
  private Integer purchaseReferenceLineNumber;
  private String purchaseReferenceNumber;
  private Integer rcvdqtytilldate;
}
