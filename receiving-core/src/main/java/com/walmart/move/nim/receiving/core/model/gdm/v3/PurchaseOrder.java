package com.walmart.move.nim.receiving.core.model.gdm.v3;

import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class PurchaseOrder {
  @NotEmpty private String poNumber;
  @NotEmpty private List<PurchaseOrderLine> lines;
  @NotEmpty private String poDcNumber;
  private String legacyType;
  @NotEmpty private Dates dates;
  @NotEmpty private String poStatus;
  @NotNull private Integer purchaseCompanyId;
  private String freightTermCode;
  @NotEmpty private String baseDivisionCode;
  @NotEmpty private PropertyDetail weight;
  private Vendor vendor;
  private Vendor vendorInformation;
  @NotEmpty private String bpoNumber;
  @NotNull private Integer freightBillQty;
  @NotEmpty private String financialGroupCode;
  private PropertyDetail cube;
  private Integer palletQty;
  private Integer totalBolFbq;
  private Map<String, String> additionalInformation;
  private String poType;
  private Integer poTypeCode;
  private Dates poDates;
  private StatusInformation statusInformation;
  private String sellerId;
  private String sellerType;
  private String sellerName;
}
