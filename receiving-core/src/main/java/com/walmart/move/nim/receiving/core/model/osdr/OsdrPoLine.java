package com.walmart.move.nim.receiving.core.model.osdr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class OsdrPoLine {
  private Long lineNumber;
  private Integer rcvdQty;
  private Integer orderFilledQty;
  private String rcvdQtyUom;
  private boolean isAtlasConvertedItem;
  private OsdrData overage;
  private OsdrData shortage;
  private OsdrData damage;
  private OsdrData reject;
  private boolean isLessThanCaseRcvd;
}
