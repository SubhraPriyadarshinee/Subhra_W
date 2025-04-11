package com.walmart.move.nim.receiving.rc.model.dto.response;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Builder
@ToString
@EqualsAndHashCode
public class FraudWorkflowStats extends WorkflowTypeStats {
  private long totalFraudItems;
  private long totalNonFraudItems;
  private long totalRegradedItems;
}
