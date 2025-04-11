package com.walmart.move.nim.receiving.core.model.gdm.v3;

import com.google.gson.annotations.Expose;
import com.walmart.move.nim.receiving.core.model.gdm.v3.pack.GdmGtinHierarchy;
import com.walmart.move.nim.receiving.core.model.gdm.v3.pack.PurchaseOrder;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Item {
  private String reportedProductId;
  private String reportedProductIdType;
  private Long itemNumber;
  private String gtin;
  private String warehouseCaseGtin;
  private String vendorCaseGtin;
  private String innerCaseGtin;
  private String vendorId;
  private String itemDepartment;
  private String itemDescription;
  private String itemDivision;
  private String itemOnFileStatus;
  @Expose private PurchaseOrder purchaseOrder;
  private InventoryDetail inventoryDetail;
  private FinancialDetail financialDetail;
  private List<ManufactureDetail> manufactureDetails;
  private Integer aggregatedItemQty;
  private String aggregatedItemQtyUom;
  private List<GdmGtinHierarchy> gtinHierarchy;
  private String nationalDrugCode;
  private String nationalDrugCodeN2;
  private String nationalDrugCodeN4;
  private InvoiceDetail invoice;
  private String replenishmentCode;
  private AdditionalInfo additionalInfo;
  private List<Item> childItems;
  private String assortmentType;
  private String serial;
  private String trackingStatus;
  private String uom;
  private String receivingStatus;
  private String replenishmentGroupNumber;
  private String deptCategory;
  private String hybridStorageFlag;
}
