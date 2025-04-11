package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReceivingProgressPayload {
  public String deliveryNumber;
  public int scheduledUnitCount;
  public int unloadedUnitCount;
  public String unitUOM;
  public int unloadPercent;
  public String trailerId;
  public String carrierId;
  public List<UnLoaderDetail> unLoaderDetails;
  public List<PurchaseOrderInfo> purchaseOrders;
}
