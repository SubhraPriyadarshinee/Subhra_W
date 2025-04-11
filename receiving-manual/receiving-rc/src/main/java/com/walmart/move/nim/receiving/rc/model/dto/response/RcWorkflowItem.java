package com.walmart.move.nim.receiving.rc.model.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.walmart.move.nim.receiving.rc.contants.RcConstants;
import java.util.Date;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Response DTO for workflow items inside a receiving workflow.
 *
 * @author m0s0mqs
 */
@Data
@Builder
@ToString
@EqualsAndHashCode
public class RcWorkflowItem {
  private Long id;
  private String itemTrackingId;
  private String gtin;
  private String action;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = RcConstants.UTC_DATE_FORMAT)
  private Date createTs;

  private String createUser;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = RcConstants.UTC_DATE_FORMAT)
  private Date lastChangedTs;

  private String lastChangedUser;
}
