package com.walmart.move.nim.receiving.core.model.audit.logs;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpHeaders;

@AllArgsConstructor
@Data
public class AuditLogRequest {

  private Long deliveryNumber;
  private String status;
  private HttpHeaders httpHeaders;
}
