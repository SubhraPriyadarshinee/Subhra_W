package com.walmart.move.nim.receiving.endgame.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LineStatusInfo {

  private Integer lineQty;
  private String lineStatus;
}
