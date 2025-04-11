package com.walmart.move.nim.receiving.rc.model.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.walmart.move.nim.receiving.rc.contants.RcConstants;
import com.walmart.move.nim.receiving.rc.model.dto.request.RcWorkflowAdditionalAttributes;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Response DTO for receiving workflow details. Contains a list of workflow items.
 *
 * @see RcWorkflowItem
 * @author m0s0mqs
 */
@Data
@Builder
@ToString
@EqualsAndHashCode
public class RcWorkflowResponse {
  private Long id;
  private String workflowId;
  private String packageBarcodeValue;
  private String packageBarcodeType;
  private String type;
  private String createReason;
  private String status;
  private List<RcWorkflowItem> workflowItems;
  private RcWorkflowAdditionalAttributes additionalAttributes;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<String> images;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String comments;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = RcConstants.UTC_DATE_FORMAT)
  private Date createTs;

  private String createUser;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = RcConstants.UTC_DATE_FORMAT)
  private Date lastChangedTs;

  private String lastChangedUser;
}
