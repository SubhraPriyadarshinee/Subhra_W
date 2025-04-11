package com.walmart.move.nim.receiving.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class Item {
  private Long number;
  private String description;
  private Integer palletTi;
  private Integer palletHi;
  private String gtin;
  private String deptNumber;
  private String secondaryDescription;
  private Boolean isConveyable;
  private String itemType;
  private Boolean isHazmat;
  private String size;
  private String color;
  private Float cube;
  private String cubeUom;
  private Float weight;
  private String weightUom;
}
