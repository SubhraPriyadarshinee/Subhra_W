package com.walmart.move.nim.receiving.rdc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HazmatVerificationRequest {
  private String itemNumber;
  private String caseUPC;
  private String itemUPC;
  private String deliveryNumber;
  private String purchaseReferenceNumber;
}
