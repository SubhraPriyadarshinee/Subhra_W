package com.walmart.move.nim.receiving.rdc.model;

import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum RdcReceivingType {
  SPLIT_PALLET_UPC("SPLIT_PALLET_UPC");

  @Getter private String receivingType;

  public boolean isSplitPalletGroup() {
    return SPLIT_PALLET_UPC.getReceivingType().equals(receivingType);
  }

  public static Optional<RdcReceivingType> fromString(String receivingTypeText) {
    for (RdcReceivingType rdcReceivingType : RdcReceivingType.values()) {
      if (rdcReceivingType.receivingType.equals(receivingTypeText)) {
        return Optional.of(rdcReceivingType);
      }
    }
    return Optional.empty();
  }
}
