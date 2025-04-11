package com.walmart.move.nim.receiving.core.model;

import com.walmart.move.nim.receiving.core.entity.Instruction;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public abstract class InstructionResponse {
  private String deliveryStatus;
  private List<DeliveryDocument> deliveryDocuments;
  private Instruction instruction;

  // For Pallet Receiving Instructions
  private List<Instruction> instructions;

  public InstructionResponse(
      String deliveryStatus, List<DeliveryDocument> deliveryDocuments, Instruction instruction) {
    this.deliveryStatus = deliveryStatus;
    this.deliveryDocuments = deliveryDocuments;
    this.instruction = instruction;
  }
}
