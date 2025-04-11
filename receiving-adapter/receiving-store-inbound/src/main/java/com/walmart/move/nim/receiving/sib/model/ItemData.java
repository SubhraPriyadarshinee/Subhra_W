package com.walmart.move.nim.receiving.sib.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ItemData {
  private String destTrackingId;
  private String invoiceNumber;
  private String locationName;
  private String itemUPC;
  private Integer itemQty;
  private String unitOfMeasurement;
  private Double derivedItemQty;
  private String derivedUnitOfMeasurement;
  private String eventType;
}
