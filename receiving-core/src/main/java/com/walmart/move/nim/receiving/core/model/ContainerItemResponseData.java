package com.walmart.move.nim.receiving.core.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ContainerItemResponseData {
  private Integer itemNumber;

  private String itemUpc;

  private Integer itemQuantity;

  private String quantityUOM;

  private String storeOrderNumber;

  private String baseDivCode;

  private ContainerPOResponseData purchaseOrder;
}
