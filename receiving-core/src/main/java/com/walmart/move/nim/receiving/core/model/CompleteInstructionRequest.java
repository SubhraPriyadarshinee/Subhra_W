package com.walmart.move.nim.receiving.core.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompleteInstructionRequest {
  private Integer printerId;
  private String printerName;
  private SlotDetails slotDetails;
  private boolean partialContainer;
  private String skuIndicator;
  private String doorNumber;
}
