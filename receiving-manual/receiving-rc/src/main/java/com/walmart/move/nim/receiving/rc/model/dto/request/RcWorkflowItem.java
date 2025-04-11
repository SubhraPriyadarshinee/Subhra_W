package com.walmart.move.nim.receiving.rc.model.dto.request;

import javax.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * DTO containing workflow item info for creating a new workflow
 *
 * @author m0s0mqs
 */
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RcWorkflowItem {
  private String itemTrackingId;

  @NotEmpty(message = "GTIN cannot be null/empty")
  private String gtin;
}
