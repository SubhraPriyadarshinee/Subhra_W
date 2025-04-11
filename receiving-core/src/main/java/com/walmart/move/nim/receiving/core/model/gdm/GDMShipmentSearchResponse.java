package com.walmart.move.nim.receiving.core.model.gdm;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GDMShipmentSearchResponse {
  List<GDMShipmentHeaderSearchResponse> data;
  GdmDeliveryHeaderDetailsPageResponse page;
}
