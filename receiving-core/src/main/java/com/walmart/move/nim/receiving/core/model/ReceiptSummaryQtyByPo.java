package com.walmart.move.nim.receiving.core.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ReceiptSummaryQtyByPo {

  private String purchaseReferenceNumber;
  private String vendorName;
  private Integer receivedQty;
  private Integer freightBillQuantity;
  private Integer totalBolFbq;
  private String freightTermCode;
  private boolean isPoFinalized;
}
