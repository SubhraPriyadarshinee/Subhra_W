package com.walmart.move.nim.receiving.rc.model.dto.request;

import com.walmart.move.nim.receiving.rc.model.gdm.SalesOrder;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Request DTO for updating a workflow
 *
 * @author m0s0mqs
 */
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RcWorkflowUpdateRequest {
  @NotEmpty(message = "workflowItems cannot be null")
  @Valid
  private List<RcWorkflowItemUpdateRequest> workflowItems;

  private String packageBarcodeType;

  private SalesOrder salesOrder;

  private String createReason;

  private RcWorkflowAdditionalAttributes additionalAttributes;
}
