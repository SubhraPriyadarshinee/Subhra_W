package com.walmart.move.nim.receiving.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ItemOverrideRequest {
  private Long itemNumber;
  private Long deliveryNumber;
  private String packTypeCode;
  private String handlingMethodCode;
  private String temporaryPackTypeCode;
  private String temporaryHandlingMethodCode;
  private String purchaseReferenceNumber;
  private int purchaseReferenceLineNumber;
  private Boolean isAtlasItem;
}
