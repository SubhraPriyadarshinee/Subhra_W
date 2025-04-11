package com.walmart.move.nim.receiving.mfc.model.gdm.newinvoice;

import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NewInvoiceLine {
  private String shipmentDocumenId;
  private String eventType;
  private List<Pallet> pallets;
  private List<Pack> packs;
  private String userId;
  private Date ts;
}
