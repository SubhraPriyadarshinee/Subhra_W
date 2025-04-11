package com.walmart.move.nim.receiving.core.client.hawkeye.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class HawkeyeLabelGroupUpdateRequest {
  private String groupType;
  private String locationId;
  private String status;
  private Label label;
}
