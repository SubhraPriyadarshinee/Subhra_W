package com.walmart.move.nim.receiving.rc.model.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.walmart.move.nim.receiving.rc.contants.RcConstants;
import com.walmart.move.nim.receiving.rc.contants.WorkflowAction;
import com.walmart.move.nim.receiving.rc.contants.WorkflowStatus;
import com.walmart.move.nim.receiving.rc.contants.WorkflowType;
import java.util.Date;
import java.util.List;
import lombok.*;

@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RcWorkflowSearchCriteria {
  private String workflowId;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = RcConstants.UTC_DATE_FORMAT)
  private Date fromCreateTs;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = RcConstants.UTC_DATE_FORMAT)
  private Date toCreateTs;

  private WorkflowType type;
  private List<WorkflowStatus> statusIn;
  private List<WorkflowAction> actionIn;
  private String packageBarcodeValue;
  private String itemTrackingId;
}
