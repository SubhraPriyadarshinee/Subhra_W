package com.walmart.move.nim.receiving.core.model.labeldownload;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RejectReason {
  NONCON_SS("GLS-RCV-BE-0001"),
  NONCON_DA("GLS-RCV-BE-0002"),
  BREAKOUT("GLS-RCV-BE-0003"),
  MASTERPACK("GLS-RCV-BE-0004"),
  CONVEYABLE_SS("GLS-RCV-BE-0005"),
  DSDC_AUDIT("GLS-RCV-BE-0006"),
  X_BLOCK("GLS-RCV-BE-0007"),
  PROBLEM_ASN("GLS-RCV-BE-0008"),
  HAZMAT("GLS-RCV-BE-0009"),
  ITEM_BLOCKED("GLS-RCV-BE-0010"),
  LIMITED_ITEM("GLS-RCV-BE-0011"),
  LITHIUM("GLS-RCV-BE-0012"),
  POCON("GLS-RCV-BE-0013"),
  RDC_LIMITED_ITEM("GLS-RCV-BE-0002"),
  RDC_MASTER_PACK("GLS-RCV-BE-0004"),
  RDC_HAZMAT("GLS-RCV-BE-0005"),
  RDC_LITHIUM_ION("GLS-RCV-BE-0006"),
  RDC_NONCON("GLS-RCV-BE-0009"),
  RDC_SSTK("GLS-RCV-BE-0010");

  private String rejectCode;
}
