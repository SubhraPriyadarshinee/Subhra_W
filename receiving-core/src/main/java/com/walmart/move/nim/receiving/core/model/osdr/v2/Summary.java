package com.walmart.move.nim.receiving.core.model.osdr.v2;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Summary {
  private List<OSDRContainer> containers;
  private List<OSDRPurchaseOrder> purchaseOrders;
}
