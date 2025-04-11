package com.walmart.move.nim.receiving.core.model.osdr.v2;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OSDRLine {
  private Integer lineNumber;
  private String gtin;
  private Integer rcvdQty;
  private String rcvdQtyUom;
  private OsdrDataWeightBased overage;
  private OsdrDataWeightBased shortage;
  private OsdrDataWeightBased damage;
  private OsdrDataWeightBased reject;
  private Double derivedRcvdQty;
  private String derivedRcvdQtyUom;
  private Integer reportedQty;
  private Double derivedReportedQty;
}
