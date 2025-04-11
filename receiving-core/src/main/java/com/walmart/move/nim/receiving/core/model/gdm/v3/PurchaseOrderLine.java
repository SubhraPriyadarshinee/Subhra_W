package com.walmart.move.nim.receiving.core.model.gdm.v3;

import java.util.Map;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class PurchaseOrderLine {
  @NotNull private Integer poLineNumber;
  @NotEmpty private QuantityDetail ordered;
  // Added openQuantity property for easiness in calculation
  private Integer openQuantity;
  @NotNull private QuantityDetail ovgThresholdLimit;
  @NotEmpty private String channel;
  @NotEmpty private ItemDetails itemDetails;
  @NotEmpty private Whpk whpk;
  @NotEmpty private Vnpk vnpk;
  @NotEmpty private String poLineStatus;
  private Vendor vendor;
  private String event;
  private Map<String, Object> additionalInformation;
  private Integer orderedQty;
  private String orderedQtyUom;
  private Integer whpkQty;
  private Float whpkSell;
  private Integer vnpkQty;
  private Float vnpkCost;
  private Float vnpkWeightQty;
  private String vnpkWeightQtyUom;
  private Float vnpkCubeQty;
  private String vnpkCubeQtyUom;
  private String bolWeightQtyUom;
  private String deptNumber;
  private String originalChannel;
  private int ovgThresholdQuantityLimit;
  private Float bolWeightQty;
  private Map<String, Object> polAdditionalFields;
  private Integer freightBillQty;
  private QuantityDetail received;
  private String sscc;
}
