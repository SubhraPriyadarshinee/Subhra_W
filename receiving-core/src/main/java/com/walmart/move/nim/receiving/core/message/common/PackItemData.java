package com.walmart.move.nim.receiving.core.message.common;

import com.walmart.move.nim.receiving.core.model.gdm.v3.InventoryDetail;
import com.walmart.move.nim.receiving.core.model.gdm.v3.pack.GdmGtinHierarchy;
import com.walmart.move.nim.receiving.core.model.gdm.v3.pack.PurchaseOrder;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@Builder
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
public class PackItemData implements Serializable {

  private static final long serialVersionUID = -4397350740970773499L;

  private String gtin;

  private String serial;

  private String receivingStatus;

  private String documentPackId;
  private String vendorId;
  private Long itemNumber;
  private Integer aggregatedItemQty;
  private String aggregatedItemQtyUom;
  private String upc;
  private String vendorCaseGtin;
  private String itemDivision;
  private Integer documentSequenceNumber;
  private PurchaseOrder purchaseOrder;
  private InventoryDetail inventoryDetail;
  private List<GdmGtinHierarchy> gtinHierarchy;
}
