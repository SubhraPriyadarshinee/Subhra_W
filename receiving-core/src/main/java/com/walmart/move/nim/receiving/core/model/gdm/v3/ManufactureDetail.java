package com.walmart.move.nim.receiving.core.model.gdm.v3;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ManufactureDetail {
  private String lotNumber;
  private String expirationDate;
  private Double reportedQuantity;
  private String reportedUom;
}
