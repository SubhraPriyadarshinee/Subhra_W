package com.walmart.move.nim.receiving.core.client.hawkeye.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class HawkeyeItemUpdateRequest {
  private String groupNumber;
  private String itemNumber;
  private String locationId;
  private String orderableGTIN;
  private String consumableGTIN;
  private String catalogGTIN;
  private String reject;
}
