package com.walmart.move.nim.receiving.core.model;

import lombok.*;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReceiveAllRequest extends ReceiveInstructionRequest {

  private boolean isReceiveAll;
  private Integer receivingTie;
  private Integer receivingHi;
}
