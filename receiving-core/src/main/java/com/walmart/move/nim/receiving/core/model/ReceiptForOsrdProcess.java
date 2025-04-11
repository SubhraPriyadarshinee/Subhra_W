package com.walmart.move.nim.receiving.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptForOsrdProcess {
  private Long deliveryNumber;
  private String purchaseReferenceNumber;
}
