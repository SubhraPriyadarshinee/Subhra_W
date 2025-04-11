package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.model.audit.logs.AuditLogRequest;
import com.walmart.move.nim.receiving.core.model.audit.logs.AuditLogResponse;

public interface AuditLogProcessor {

  public AuditLogResponse getAuditLogs(AuditLogRequest auditLogRequest);
}
