package com.walmart.move.nim.receiving.core.common;

import java.util.Arrays;
import lombok.Getter;

@Getter
public enum AuditStatus {
  NOT_REQUIRED("NotRequired"),
  PENDING("Pending"),
  COMPLETED("Completed"),
  CANCELLED("Cancelled");

  private String status;

  AuditStatus(String status) {
    this.status = status;
  }

  public static AuditStatus valueOfStatus(String status) {
    return Arrays.stream(AuditStatus.values())
        .filter(auditStatus -> auditStatus.getStatus().equalsIgnoreCase(status))
        .findFirst()
        .orElse(null);
  }
}
