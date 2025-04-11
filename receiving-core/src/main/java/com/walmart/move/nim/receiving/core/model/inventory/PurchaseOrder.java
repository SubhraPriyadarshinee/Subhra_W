package com.walmart.move.nim.receiving.core.model.inventory;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
public class PurchaseOrder implements Reference {

  public String poNumber;
  public String poLineNumber;
}
