package com.walmart.move.nim.receiving.core.model.gdm;

import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GDMShipmentHeaderSearchResponse {
  private List<Delivery> delivery;
  private List<Shipment> shipments;
}
