package com.walmart.move.nim.receiving.rc.model.dto.request;

import com.walmart.move.nim.receiving.rc.contants.*;
import java.util.Map;
import lombok.*;

@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RcWorkflowSearchRequest {
  private RcWorkflowSearchCriteria criteria;
  private Map<WorkflowSortColumn, SortOrder> sortBy;
}
