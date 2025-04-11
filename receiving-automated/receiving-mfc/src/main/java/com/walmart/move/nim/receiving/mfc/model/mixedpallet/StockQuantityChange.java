package com.walmart.move.nim.receiving.mfc.model.mixedpallet;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class StockQuantityChange {

  private Double quantity;
  private String reasonCode;
  private String reasonDesc;
  private String location;
  private String expiryDate;
  private String oldLocation;
  private String newLocation;
  private String currentState;
}
