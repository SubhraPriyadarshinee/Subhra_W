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
public class InstructionResponseImplOld extends InstructionResponse {

  private List<Map<String, Object>> printJob;

  public InstructionResponseImplOld(
      String deliveryStatus,
      List<DeliveryDocument> deliveryDocuments,
      Instruction instruction,
      List<Map<String, Object>> printJob) {
    super(deliveryStatus, deliveryDocuments, instruction);
    this.printJob = printJob;
  }
}
