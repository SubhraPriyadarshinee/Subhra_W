package com.walmart.move.nim.receiving.rx.model;

import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum RxReceivingType {
  SSCC("SSCC"),
  TWOD_BARCODE("2D_BARCODE"),
  UPC("UPC"),
  TWOD_BARCODE_PARTIALS("2D_BARCODE_PARTIAL"),
  UPC_PARTIALS("UPC_PARTIAL"),
  SPLIT_PALLET_SSCC("SPLIT_PALLET_SSCC"),
  SPLIT_PALLET_TWOD_BARCODE("SPLIT_PALLET_2D_BARCODE"),
  SPLIT_PALLET_UPC("SPLIT_PALLET_UPC"),
  MULTI_SKU_FLOW("MULTI_SKU_FLOW"),
  SPLIT_PALLET_MULTI_SKU_FLOW("SPLIT_PALLET_MULTI_SKU_FLOW");

  @Getter private String receivingType;

  public boolean isSplitPalletGroup() {
    return (SPLIT_PALLET_SSCC.getReceivingType().equals(receivingType)
        || SPLIT_PALLET_TWOD_BARCODE.getReceivingType().equals(receivingType)
        || SPLIT_PALLET_UPC.getReceivingType().equals(receivingType)
    || SPLIT_PALLET_MULTI_SKU_FLOW.getReceivingType().equals(receivingType));
  }

  public boolean isPartialInstructionGroup() {
    return (TWOD_BARCODE_PARTIALS.getReceivingType().equalsIgnoreCase(receivingType)
        || UPC_PARTIALS.getReceivingType().equalsIgnoreCase(receivingType));
  }

  public static Optional<RxReceivingType> fromString(String receivingTypeText) {
    for (RxReceivingType receivingType : RxReceivingType.values()) {
      if (receivingType.receivingType.equals(receivingTypeText)) {
        return Optional.of(receivingType);
      }
    }
    return Optional.empty();
  }
}
