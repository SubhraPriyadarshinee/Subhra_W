package com.walmart.move.nim.receiving.core.model.move;

import lombok.*;

@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CancelMove {
  private String moveEvent;
  private String containerTag;
  private MoveType moveType;
}
