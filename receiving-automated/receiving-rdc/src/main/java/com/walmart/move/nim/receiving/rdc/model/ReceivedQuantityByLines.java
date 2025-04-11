package com.walmart.move.nim.receiving.rdc.model;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceivedQuantityByLines {
  @NonNull private Integer purchaseReferenceLineNumber;
  @NonNull private String purchaseReferenceNumber;
  private Integer receivedQty;
}
