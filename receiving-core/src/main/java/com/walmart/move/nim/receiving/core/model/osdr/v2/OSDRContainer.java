package com.walmart.move.nim.receiving.core.model.osdr.v2;

import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OSDRContainer {
  private String trackingId;
  private String sscc;
  private String containerType; // PALLET / PACK
  private String shipmentDocumentId;
  private Integer rcvdQty;
  private String rcvdQtyUom;
  private Double rcvdQtyWithWeightBased;
  private String uomType;
  private List<OSDRItem> items;
  private OsdrDataWeightBased overage;
  private OsdrDataWeightBased shortage;
  private OsdrDataWeightBased damage;
  private OsdrDataWeightBased reject;
  private String receivedFor;
  private Double derivedRcvdQty;
  private String derivedRcvdQtyUom;
  private Integer reportedQty;
  private Double derivedReportedQty;

  public void addRcvdQty(Integer rcvdQty) {
    this.rcvdQty += rcvdQty;
  }

  public void addDerivedRcvdQty(Double derivedRcvdQty) {
    this.derivedRcvdQty += derivedRcvdQty;
  }

  public void addReportedQty(Integer reportedQty) {
    if (Objects.isNull(this.reportedQty)) {
      this.reportedQty = 0;
    }
    this.reportedQty += reportedQty;
  }

  public void addDerivedReportedQty(Double derivedReportedQty) {
    if (Objects.isNull(this.derivedReportedQty)) {
      this.derivedReportedQty = 0.0;
    }
    this.derivedReportedQty += derivedReportedQty;
  }
}
