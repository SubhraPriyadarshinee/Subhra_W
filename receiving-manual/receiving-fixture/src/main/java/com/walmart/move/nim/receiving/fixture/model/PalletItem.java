package com.walmart.move.nim.receiving.fixture.model;

import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PalletItem {
  @NotNull private Long itemNumber;
  private Integer receivedQty;
  private Integer orderedQty;
  private String itemDescription;
  private String purchaseReferenceNumber;
  private Integer purchaseReferenceLineNumber;
  private String quantityUOM;
}
