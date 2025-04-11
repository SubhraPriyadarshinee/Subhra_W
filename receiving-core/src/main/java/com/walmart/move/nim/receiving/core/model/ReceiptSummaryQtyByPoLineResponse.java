package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ReceiptSummaryQtyByPoLineResponse {

  private String purchaseReferenceNumber;
  private String receivedQtyUom;
  private List<ReceiptSummaryQtyByPoLine> summary;
  private boolean isPoFinalized;
  // aggregate all line's receivedQty to PoLevel
  Integer totalReceivedQty = 0;
  Integer totalFreightBillQty = 0;
}
