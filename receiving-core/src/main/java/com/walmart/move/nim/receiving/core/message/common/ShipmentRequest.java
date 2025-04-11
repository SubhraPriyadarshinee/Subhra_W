package com.walmart.move.nim.receiving.core.message.common;

import java.util.List;
import lombok.*;

@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentRequest {
  private ShipmentInfo shipment;
  private List<ShipmentInfo> shipments;
}
