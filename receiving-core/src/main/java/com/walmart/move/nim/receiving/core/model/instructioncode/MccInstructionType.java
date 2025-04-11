package com.walmart.move.nim.receiving.core.model.instructioncode;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MccInstructionType {
  MX_MCC_RCV_BUILD_CONTAINER(
      "Intl Build Container", "Intl Build Container"); // Intl build container
  private final String instructionCode;
  private final String instructionMsg;
}
