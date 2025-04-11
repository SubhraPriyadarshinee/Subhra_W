package com.walmart.move.nim.receiving.endgame.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.walmart.move.nim.receiving.core.model.PalletTiHi;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class ItemDetails {
  @NotNull private Long number;
  @NotEmpty private String orderableGTIN;
  @NotEmpty private String consumableGTIN;
  private String warehousePackGTIN;
  private String catalogGTIN;
  @NotEmpty private String supplierStockId;
  private String color;
  private String itemTypeCode;
  @NotEmpty private List<String> descriptions;
  private String size;
  @NotNull private Boolean conveyable;
  private Boolean hazmat;
  @NotNull private Integer palletTi;
  @NotNull private Integer palletHi;
  @NotNull private Integer vendorDepartment;
  private Map<String, Object> additionalInformation;
  private Map<String, String> itemAdditonalInformation;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private PalletTiHi palletTiHi;
}
