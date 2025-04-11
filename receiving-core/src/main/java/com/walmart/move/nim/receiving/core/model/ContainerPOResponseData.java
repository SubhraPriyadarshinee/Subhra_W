package com.walmart.move.nim.receiving.core.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ContainerPOResponseData {
  private String purchaseReferenceNumber;

  private Integer purchaseReferenceLineNumber;

  private Integer purchaseReferenceType;

  private Double warehousePackSell;

  private Integer warehousePackQuantity;

  private Integer vendorPackQuantity;

  private String financialGroupCode;
}
