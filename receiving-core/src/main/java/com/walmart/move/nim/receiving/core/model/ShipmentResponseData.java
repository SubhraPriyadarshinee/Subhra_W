package com.walmart.move.nim.receiving.core.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShipmentResponseData {
  private String shipmentNumber;

  private String bolNumber;

  private String loadNumber;

  private ContainerResponseData container;
}
