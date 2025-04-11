package com.walmart.move.nim.receiving.rc.model.dto.request;

import com.walmart.move.nim.receiving.rc.contants.WorkflowAction;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Request DTO for updating a workflow item
 *
 * @author m0s0mqs
 */
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RcWorkflowItemUpdateRequest {
  @NotNull(message = "ID cannot be null")
  private Long id;

  @NotNull(message = "Action cannot be null")
  private WorkflowAction action;

  private String itemTrackingId;
}
