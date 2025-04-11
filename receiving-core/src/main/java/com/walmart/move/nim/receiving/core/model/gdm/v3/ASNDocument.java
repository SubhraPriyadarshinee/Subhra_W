package com.walmart.move.nim.receiving.core.model.gdm.v3;

import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class ASNDocument extends SsccScanResponse {
  @NotEmpty private List<ItemDetails> items;
  @NotEmpty private Shipment shipment;
  // This will get populated only for Store IB when associate is marking a pallet as overage
  private boolean isOverage;
}
