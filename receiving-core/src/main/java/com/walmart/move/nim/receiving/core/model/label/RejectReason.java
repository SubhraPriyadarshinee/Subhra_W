package com.walmart.move.nim.receiving.core.model.label;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RejectReason {
  NONCON_SSTK("GLS-RCV-BE-0001"),
  NONCON_DA("GLS-RCV-BE-0002"),
  BREAKOUT("GLS-RCV-BE-0003"),
  MASTERPACK("GLS-RCV-BE-0004"),
  CONVEYABLE_SSTK("GLS-RCV-BE-0005"),
  DSDC_AUDIT("GLS-RCV-BE-0006"),
  AUDIT("GLS-RCV-BE-0007"),
  PROBLEM_ASN("GLS-RCV-BE-0008"),
  HAZMAT("GLS-RCV-BE-0009"),
  ITEM_BLOCKED("GLS-RCV-BE-0010"),
  LIMITED_ITEM("GLS-RCV-BE-0011"),
  LITHIUM("GLS-RCV-BE-0012"),
  NONCON_SSTK_FLIP("GLS-RCV-BE-0013"),
  NONCON_DA_FLIP("GLS-RCV-BE-0014"),
  DA_ATLAS_ITEM("GLS-RCV-BE-0015"),
  SSTK_ATLAS_ITEM("GLS-RCV-BE-0016");
  private String rejectCode;
}
