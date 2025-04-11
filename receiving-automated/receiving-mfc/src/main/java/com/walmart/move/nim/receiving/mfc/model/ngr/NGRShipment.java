package com.walmart.move.nim.receiving.mfc.model.ngr;

import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import lombok.Builder;
import lombok.Data;

/** POJO for sending shipment arrival event to NGR */
@Data
@Builder
public class NGRShipment {
  private Delivery delivery;
  private Shipment shipment;
}
