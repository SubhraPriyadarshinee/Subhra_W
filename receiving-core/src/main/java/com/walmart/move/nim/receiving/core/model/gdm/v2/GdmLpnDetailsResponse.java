package com.walmart.move.nim.receiving.core.model.gdm.v2;

import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode
public class GdmLpnDetailsResponse {

  private Delivery delivery;
  private List<Pack> packs;
  private List<Shipment> shipments;

  @Data
  @NoArgsConstructor
  public static class Delivery {
    private long deliveryNumber;
  }
}
