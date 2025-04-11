package com.walmart.move.nim.receiving.witron.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum GdcInstructionType {
  AUTO_GROC_BUILD_PALLET("AutoGrocBuildPallet", "Auto Groc Build Pallet"),
  MANL_GROC_BUILD_PALLET("ManlGrocBuildPallet", "Manl Groc Build Pallet");

  private String instructionCode;
  private String instructionMsg;
}
