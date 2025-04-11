package com.walmart.move.nim.receiving.core.model.gdm;

import com.walmart.move.nim.receiving.core.message.common.PackData;
import com.walmart.move.nim.receiving.core.model.gdm.v3.AsnDelivery;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GdmAsnDeliveryResponse {
  private AsnDelivery delivery;
  private List<Shipment> shipments;
  private List<PackData> packs;
  private List<PurchaseOrder> purchaseOrders;
}
