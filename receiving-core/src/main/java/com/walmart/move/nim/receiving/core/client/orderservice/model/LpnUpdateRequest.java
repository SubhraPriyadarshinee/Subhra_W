package com.walmart.move.nim.receiving.core.client.orderservice.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LpnUpdateRequest {
  private String purchaseReferenceNumber;
  private Long itemNumber;
  private List<LpnsInfo> failedLpns;
  private List<LpnsInfo> successLpns;
}
