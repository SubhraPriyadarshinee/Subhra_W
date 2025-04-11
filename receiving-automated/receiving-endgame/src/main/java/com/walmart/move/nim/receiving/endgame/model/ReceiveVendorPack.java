package com.walmart.move.nim.receiving.endgame.model;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@ToString
public class ReceiveVendorPack {
  private Container container;
  private List<PurchaseOrder> purchaseOrderList;
}
