package com.walmart.move.nim.receiving.witron.service;

import lombok.Data;

@Data
public class DeliveryCacheValue {

  private Integer totalBolFbq;
  private Float bolWeight;
  private String trailerId;
  private String scacCode;
  private String freightTermCode;
  private String purchaseReferenceLegacyType;
}
