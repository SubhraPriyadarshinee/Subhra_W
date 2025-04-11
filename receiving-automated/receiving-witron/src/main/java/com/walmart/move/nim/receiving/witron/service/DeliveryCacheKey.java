package com.walmart.move.nim.receiving.witron.service;

import lombok.Data;

@Data
public class DeliveryCacheKey {

  private Long deliveryNumber;
  private String purchaseReferenceNumber;
  private Integer purchaseReferenceLineNumber;
}
