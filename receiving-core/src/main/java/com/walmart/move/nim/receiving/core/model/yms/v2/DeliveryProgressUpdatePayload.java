package com.walmart.move.nim.receiving.core.model.yms.v2;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Setter
@Getter
public class DeliveryProgressUpdatePayload {
  private String deliveryNumber;
  private List<DeliveryUnloaderDetails> unLoaderDetails;
  private List<DeliveryPurchaseOrder> purchaseOrders;
  private Long scheduledUnitCount;
  private Long unloadedUnitCount;
  private String unitUOM = "CASES";
  private double unloadPercent;
}
