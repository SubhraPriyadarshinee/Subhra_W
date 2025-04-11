package com.walmart.move.nim.receiving.rc.model.item;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemDetails {
  private Long number;
  private Integer legacyItemId;
  private String itemId;
  private String legacySellerId;
  private String serviceType;
  private String baseDivisionCode;
  private String financialReportingGroupCode;
  private String orderableGTIN;
  private String consumableGTIN;
  private String wupc;
  private String productName;
  private String brandName;
  private List<String> description;
  private String vendorNumber;
  private String vendorName;
  private String offerId;
  private String offerPublishStatus;
  private String batteryTypeCode;
  private String ironbankCategory;
  private String partnershipTypeCode;
  private String shelfLife;
  private Boolean isChemical;
  private Boolean isAerosol;
  private Boolean isFood;
  private Boolean isHazmat;
  private Boolean isHazardous;
  private String regulatedItemType;
  private String regulatedItemLabelCode;
  private Boolean isPesticide;
  private Boolean isConsumable;
  private Boolean isFragile;
  private Dimensions dimensions;
  private Weight weight;
  private Cube cube;
  private String goodwillReason;
  private Boolean isGoodwill;
  private List<String> serialNumbers;
}
