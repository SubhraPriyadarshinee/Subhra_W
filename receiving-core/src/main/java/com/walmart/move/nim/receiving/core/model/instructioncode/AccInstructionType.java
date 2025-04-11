package com.walmart.move.nim.receiving.core.model.instructioncode;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AccInstructionType {
  ACC_MANUAL_RCV_BUILD_PALLET("ACCManualReceivingBuildPallet", "ACC Manual Receiving Build Pallet");
  private final String instructionCode;
  private final String instructionMsg;
}
