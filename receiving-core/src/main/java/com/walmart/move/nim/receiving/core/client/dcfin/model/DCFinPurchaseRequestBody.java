package com.walmart.move.nim.receiving.core.client.dcfin.model;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class DCFinPurchaseRequestBody {
  private String txnId;
  private List<Purchase> purchase;
}
