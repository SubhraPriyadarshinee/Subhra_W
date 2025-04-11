package com.walmart.move.nim.receiving.core.model.osdr;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CutOverType {
  MIXED("Mixed"),
  ATLAS("Atlas"),
  NON_ATLAS("Non-Atlas");

  private String type;
}
