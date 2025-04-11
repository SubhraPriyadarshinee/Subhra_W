package com.walmart.move.nim.receiving.endgame.model;

import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class AuditChangeRequest {
  @NotNull(message = "SearchCriteria cannot be null/empty")
  private SearchCriteria searchCriteria;

  @NotNull(message = "AuditInfo cannot be null/empty")
  private AuditInfo auditInfo;
}
