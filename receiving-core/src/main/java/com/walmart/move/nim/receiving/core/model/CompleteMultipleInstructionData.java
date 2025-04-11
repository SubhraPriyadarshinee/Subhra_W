package com.walmart.move.nim.receiving.core.model;

import javax.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** @author v0k00fe */
@Data
@EqualsAndHashCode
public class CompleteMultipleInstructionData {

  @NotBlank private Long instructionId;
  private SlotDetails slotDetails;
}
