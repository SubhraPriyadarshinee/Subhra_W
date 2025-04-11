package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@NoArgsConstructor
public class PublishReceiptsCancelInstruction {
  private String messageId;
  private Long deliveryNumber;
  private String trackingId;
  private List<ContentsData> contents;
  private String activityName;

  public static class ContentsData {
    private String purchaseReferenceNumber;
    private Integer purchaseReferenceLineNumber;
    private Integer quantity;
    private String quantityUOM;

    public ContentsData(
        String purchaseReferenceNumber,
        Integer purchaseReferenceLineNumber,
        Integer quantity,
        String quantityUOM) {
      this.purchaseReferenceNumber = purchaseReferenceNumber;
      this.purchaseReferenceLineNumber = purchaseReferenceLineNumber;
      this.quantity = quantity;
      this.quantityUOM = quantityUOM;
    }
  }
}
