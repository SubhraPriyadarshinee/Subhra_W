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
public class InstructionResponseImplNew extends InstructionResponse {

  private Map<String, Object> printJob;
  private String parentTrackingId;

  public InstructionResponseImplNew(
      String deliveryStatus,
      List<DeliveryDocument> deliveryDocuments,
      Instruction instruction,
      Map<String, Object> printJob) {
    super(deliveryStatus, deliveryDocuments, instruction);
    this.printJob = printJob;
  }

  public InstructionResponseImplNew(
      String deliveryStatus,
      List<DeliveryDocument> deliveryDocuments,
      Instruction instruction,
      Map<String, Object> printJob,
      String parentTrackingId) {
    this(deliveryStatus, deliveryDocuments, instruction, printJob);
    this.parentTrackingId = parentTrackingId;
  }
}
