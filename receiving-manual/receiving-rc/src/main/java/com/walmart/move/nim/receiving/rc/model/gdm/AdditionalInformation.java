package com.walmart.move.nim.receiving.rc.model.gdm;

import java.util.Map;
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
public class AdditionalInformation {
  private Map<String, String> images;
  private Boolean consumable;
  private Boolean wfsItem;
  private Float weight;
  private String weightUOM;
  private String dotHazardousCode;
  private String brandName;
  private String hasExpiration;
  private String physicalState;
  private String ironBankCategory;
  private String shelfLife;
  private String productName;
  private Double cubeQty;
  private String cubeUomCode;
  private Dimensions dimensions;
}
