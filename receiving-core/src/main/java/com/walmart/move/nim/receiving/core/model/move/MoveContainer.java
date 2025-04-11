package com.walmart.move.nim.receiving.core.model.move;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoveContainer {

  private String trackingId;
  private Integer moveQty;
}
