package com.walmart.move.nim.receiving.core.model;

import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RejectPalletRequest {
  private String consumableGTIN;
  private Long deliveryNumber;
  private String doorNumber;
  private Long itemNumber;
  private String orderableGTIN;
  private Integer purchaseReferenceLineNumber;
  private String purchaseReferenceNumber;
  private Integer rejectedQty;
  private String rejectedReasonCode;
  private String rejectedUOM;
  private String rejectionComment;
  private Date rotateDate;
  private Integer vnpkQty;
  private Integer whpkQty;
}
