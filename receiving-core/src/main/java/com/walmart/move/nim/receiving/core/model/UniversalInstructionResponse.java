package com.walmart.move.nim.receiving.core.model;

import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class UniversalInstructionResponse {
  private String deliveryStatus;
  private List<DeliveryDocument> deliveryDocuments;
  private Instruction instruction;

  // For Pallet Receiving Instructions
  private List<Instruction> instructions;

  private DeliveryDetails delivery;

  private LocationInfo locationInfo;
}
