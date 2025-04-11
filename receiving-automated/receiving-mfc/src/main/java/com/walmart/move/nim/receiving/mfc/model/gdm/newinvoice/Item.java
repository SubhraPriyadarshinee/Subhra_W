package com.walmart.move.nim.receiving.mfc.model.gdm.newinvoice;

import com.walmart.move.nim.receiving.core.model.gdm.v3.InventoryDetail;
import com.walmart.move.nim.receiving.core.model.gdm.v3.InvoiceDetail;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Item {
  private Long itemNumber;
  private String gtin;
  private String itemDescription;
  private InventoryDetail inventoryDetail;
  private InvoiceDetail invoice;
  private String replenishmentCode;
  private String itemDepartment;
  private String hybridStorageFlag;
}
