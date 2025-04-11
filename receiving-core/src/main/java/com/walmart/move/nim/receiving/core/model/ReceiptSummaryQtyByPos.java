package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ReceiptSummaryQtyByPos {

  private String rcvdQtyUOM;
  private List<String> poNumbers;
}
