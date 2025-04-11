package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ContainerResponseData {
  private String label;

  private String channel;

  private Integer invoiceNumber;

  private Integer destinationNumber;

  private String destinationType;

  private String destinationCountryCode;

  private Integer sourceNumber;

  private String sourceType;

  private String sourceCountryCode;

  private Float weight;

  private String weightUOM;

  private Float cube;

  private String cubeUOM;

  private List<ContainerItemResponseData> items;

  private List<ContainerResponseData> containers;
}
