package com.walmart.move.nim.receiving.core.model;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@Getter
@Setter
@AllArgsConstructor
public class ShipmentDetails {

  private String inboundShipmentDocId;
  private String shipmentNumber;
  private String trailerNumber;
  private String trailerSealNumber;
  private String loadNumber;
  private String sourceGlobalLocationNumber;
  private String shipperId;
  private String destinationGlobalLocationNumber;
  private Integer shippedQty;
  private String shippedQtyUom;
  private String documentType;
  private Long reportedDeliveryNumber;
  private String carrierId;
}
