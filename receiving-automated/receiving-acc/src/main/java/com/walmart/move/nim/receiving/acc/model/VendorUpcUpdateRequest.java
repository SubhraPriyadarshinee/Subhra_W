package com.walmart.move.nim.receiving.acc.model;

import lombok.*;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VendorUpcUpdateRequest {
  private String deliveryNumber;
  private String itemNumber;
  private String locationId;
  private String catalogGTIN;
  private String orderableGTIN;
}
