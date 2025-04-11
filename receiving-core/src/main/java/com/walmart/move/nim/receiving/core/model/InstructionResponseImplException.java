package com.walmart.move.nim.receiving.core.model;

import com.walmart.move.nim.receiving.core.entity.Instruction;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor
public class InstructionResponseImplException extends InstructionResponseImplNew {

  private ExceptionInstructionMsg exceptionInstructionMsg;

  private boolean ignoreExceptionMessageCheckForOverage;
  private Boolean isAtlasReceivedContainer = Boolean.FALSE;

  public InstructionResponseImplException(
      String deliveryStatus,
      List<DeliveryDocument> deliveryDocuments,
      Instruction instruction,
      Map<String, Object> printJob,
      ExceptionInstructionMsg exceptionInstructionMsg,
      boolean ignoreExceptionMessageCheckForOverage,
      boolean isAtlasReceivedContainer) {
    super(deliveryStatus, deliveryDocuments, instruction, printJob);
    this.exceptionInstructionMsg = exceptionInstructionMsg;
    this.ignoreExceptionMessageCheckForOverage = ignoreExceptionMessageCheckForOverage;
    this.isAtlasReceivedContainer = isAtlasReceivedContainer;
  }
}
