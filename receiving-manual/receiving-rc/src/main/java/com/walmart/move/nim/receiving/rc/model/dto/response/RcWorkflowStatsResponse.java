package com.walmart.move.nim.receiving.rc.model.dto.response;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Builder
@ToString
@EqualsAndHashCode
public class RcWorkflowStatsResponse<T> {

  private long totalWorkflows;
  private long totalWorkflowItems;
  private long totalOpenWorkflows;
  private long totalClosedWorkflows;
  private long totalPendingWorkflowItems;
  private T statsByWorkflowType;
}
