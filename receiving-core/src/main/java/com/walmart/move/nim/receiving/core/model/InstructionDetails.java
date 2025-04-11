package com.walmart.move.nim.receiving.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class InstructionDetails {
  private Long id;
  private Long deliveryNumber;
  private String createUserId;
  private String lastChangeUserId;
  private int receivedQuantity;
}
