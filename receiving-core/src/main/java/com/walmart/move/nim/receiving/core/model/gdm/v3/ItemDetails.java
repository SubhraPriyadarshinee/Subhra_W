package com.walmart.move.nim.receiving.core.model.gdm.v3;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.walmart.move.nim.receiving.core.model.PalletTiHi;
import com.walmart.move.nim.receiving.core.model.TransportationModes;
import java.util.Date;
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
  private Map<String, Object> itemAdditonalInformation;
  private Integer warehousePackQuantity;
  private Integer orderableQuantity;

  private String handlingCode;
  private Boolean isTemperatureSensitive = false;
  private Boolean isControlledSubstance = false;
  private String packType;

  private Boolean isNewItem;

  private Date limitedIonVerifiedOn;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private PalletTiHi palletTiHi;

  private Date limitedQtyVerifiedOn;
  private Integer pluNumber;
  private List<TransportationModes> transportationModes;
  private Boolean complianceItem;
  private Integer actualTi;
  private Integer actualHi;
}
