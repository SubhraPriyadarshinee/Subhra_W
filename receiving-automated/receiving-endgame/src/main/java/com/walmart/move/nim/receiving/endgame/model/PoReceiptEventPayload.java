package com.walmart.move.nim.receiving.endgame.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PoReceiptEventPayload {
  private String poNumber;
  private String sellerId;
  private Integer shipNode;
  private String shipNodeCountry;
  private List<PoLineReceipt> poLines;
}
