package com.walmart.move.nim.receiving.core.model.delivery;

import com.walmart.move.nim.receiving.core.model.gdm.v3.ConsolidatedDelivery;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class TCLFreeDeliveryDetails {
  private Long deliveryNumber;
  private String Gtin;
  private List<ConsolidatedDelivery> listOfDeliveries;
  private AtomicBoolean multipleSellersAvailable;
  private AtomicBoolean singlePo;
  private String poNumber;
}
