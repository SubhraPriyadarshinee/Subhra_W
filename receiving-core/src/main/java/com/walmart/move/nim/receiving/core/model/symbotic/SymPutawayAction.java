package com.walmart.move.nim.receiving.core.model.symbotic;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SymPutawayAction {
  ADD("add"),
  UPDATE("update"),
  DELETE("delete");

  private String action;
}
