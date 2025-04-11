package com.walmart.move.nim.receiving.rdc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RdcItemUpdateRequest {
  private Long itemNumber;
  private String temporaryPackCode;
  private String temporaryHandlingType;
  private String purchaseReferenceNumber;
  private String delivery;
  private int purchaseReferenceLineNumber;
}
