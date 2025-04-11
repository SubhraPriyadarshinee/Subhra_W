package com.walmart.move.nim.receiving.witron.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HaccpError {
  String deliveryNumber;
  String purchaseReferenceNumber;
  int purchaseReferenceLineNumber;
  Long itemNbr;
  String itemDescription;
  String secondaryItemDescription;
}
