package com.walmart.move.nim.receiving.rc.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Response DTO for receiving create container API
 *
 * @author m0s0mqs
 */
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReceiveContainerResponse {
  private Long id;
  private String trackingId;
  private String workflowId;
}
