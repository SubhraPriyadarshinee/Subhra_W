package com.walmart.move.nim.receiving.acc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HawkEyeVendorUpcUpdateRequest {
  private Long deliveryNumber;
  private Long itemNumber;
  private String locationId;
  private String catalogGTIN;
  private String orderableGTIN;
}
