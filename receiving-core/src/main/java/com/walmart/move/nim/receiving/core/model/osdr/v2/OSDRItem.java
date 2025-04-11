package com.walmart.move.nim.receiving.core.model.osdr.v2;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OSDRItem {
  private String itemNumber;
  private String gtin;
  private String invoiceNumber;
  private String invoiceLineNumber;
  private Integer reportedQty;
  private String reportedQtyUom;
  private Integer rcvdQty;
  private String rcvdQtyUom;
  private OsdrDataWeightBased overage;
  private OsdrDataWeightBased shortage;
  private OsdrDataWeightBased damage;
  private OsdrDataWeightBased reject;
  private Double derivedReportedQty;
  private String derivedReportedQtyUom;
  private Double derivedRcvdQty;
  private String derivedRcvdQtyUom;
}
