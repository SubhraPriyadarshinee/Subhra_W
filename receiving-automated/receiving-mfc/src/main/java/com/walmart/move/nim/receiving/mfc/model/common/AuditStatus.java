package com.walmart.move.nim.receiving.mfc.model.common;

import java.util.Arrays;
import java.util.List;

public enum AuditStatus {
  SUCCESS("SUCCESS"),
  IN_PROGRESS("IN_PROGRESS"),
  FAILURE("FAILURE");

  private String auditStatus;

  AuditStatus(String auditStatus) {
    this.auditStatus = auditStatus;
  }

  public String getAuditStatus() {
    return auditStatus;
  }

  public static List<AuditStatus> getInvalidStatusForReprocessing() {
    return Arrays.asList(AuditStatus.SUCCESS, AuditStatus.IN_PROGRESS);
  }
}
