package com.walmart.move.nim.receiving.mfc.model.csm;

import lombok.*;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ContainerEventItem {
  private String destTrackingId;
  private String invoiceNumber;
  private String locationName;
  private String itemUPC;
  private Integer itemQty;
  private String unitOfMeasurement;
}
