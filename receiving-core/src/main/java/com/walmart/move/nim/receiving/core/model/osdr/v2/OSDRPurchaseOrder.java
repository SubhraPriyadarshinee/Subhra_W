package com.walmart.move.nim.receiving.core.model.osdr.v2;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OSDRPurchaseOrder {
  private String purchaseReferenceNumber;
  private Integer rcvdQty;
  private String rcvdQtyUom;
  private Integer rcvdQtyWithWeightBased;
  private String uomType;
  private String invoiceNumber;
  private List<OSDRLine> lines;
  private OsdrDataWeightBased overage;
  private OsdrDataWeightBased shortage;
  private OsdrDataWeightBased damage;
  private OsdrDataWeightBased reject;
  private String receivedFor;
  private Double derivedRcvdQty;
  private String derivedRcvdQtyUom;
  private Integer reportedQty;
  private Double derivedReportedQty;

  public void addReportedQty(Integer reportedQty) {
    this.reportedQty += reportedQty;
  }

  public void addDerivedReportedQty(Double derivedReportedQty) {
    this.derivedReportedQty += derivedReportedQty;
  }
}
