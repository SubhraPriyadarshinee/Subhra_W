package com.walmart.move.nim.receiving.core.model.audit.logs;

import java.util.List;
import lombok.Data;

@Data
public class AuditLogDetails {

  private Long deliveryNumber;
  private Integer pendingAuditCount;
  private List<AuditLogSummary> packSummary;
}
