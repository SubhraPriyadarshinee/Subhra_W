package com.walmart.move.nim.receiving.core.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
@AllArgsConstructor
public class POPOLineKey {

  private String purchaseReferenceNumber;
  private Integer purchaseReferenceLineNumber;
}
