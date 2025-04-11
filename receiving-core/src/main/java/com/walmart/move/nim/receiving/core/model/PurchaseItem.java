package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class PurchaseItem {
  private String deliveryNum;
  private String documentNum;
  private List<LinesItem> lines;
  private Boolean importInd;
  private String poDCNumber;
  private String poDcCountry;
}
