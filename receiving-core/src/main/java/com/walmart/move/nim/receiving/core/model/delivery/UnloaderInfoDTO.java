package com.walmart.move.nim.receiving.core.model.delivery;

import com.google.gson.annotations.Expose;
import lombok.Data;

@Data
public class UnloaderInfoDTO {

  @Expose private Long deliveryNumber;
  @Expose private String purchaseReferenceNumber;
  @Expose private Integer purchaseReferenceLineNumber;
  @Expose private Long itemNumber;
  @Expose private Integer actualTi;
  @Expose private Integer actualHi;
  @Expose private Integer fbq;
  @Expose private Integer caseQty;
  @Expose private Integer palletQty;
  @Expose private boolean unloadedFullFbq;
  @Expose private Integer orgUnitId;
}
