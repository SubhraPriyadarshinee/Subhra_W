package com.walmart.move.nim.receiving.endgame.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class PalletMoveRequest {
  private String userId;
  private String status;
  private SlotMoveType moveType;
  private List<String> containerIds;
  private boolean nextMove;
  private boolean isSkipped;
  private String skipReason;
}
