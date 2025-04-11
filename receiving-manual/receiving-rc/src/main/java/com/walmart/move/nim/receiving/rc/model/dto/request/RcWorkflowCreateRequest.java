package com.walmart.move.nim.receiving.rc.model.dto.request;

import com.walmart.move.nim.receiving.rc.contants.WorkflowType;
import com.walmart.move.nim.receiving.rc.model.gdm.SalesOrder;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Request DTO for creating a new workflow. Contains a list of workflow items.
 *
 * @see RcWorkflowItem
 * @author m0s0mqs
 */
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RcWorkflowCreateRequest {
  @NotEmpty(message = "WorkflowId cannot be null/empty")
  private String workflowId;

  @NotEmpty(message = "PackageBarCodeValue cannot be null/empty")
  private String packageBarcodeValue;

  @NotNull(message = "Type cannot be null")
  private WorkflowType type;

  @NotEmpty(message = "CreateReason cannot be null/empty")
  @Size(min = 1, max = 150, message = "CreateReason size can not be more than 150")
  private String createReason;

  @Valid private List<RcWorkflowItem> items;

  private String packageBarcodeType;

  private SalesOrder salesOrder;

  private RcWorkflowAdditionalAttributes additionalAttributes;
}
