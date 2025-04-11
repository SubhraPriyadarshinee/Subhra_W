package com.walmart.move.nim.receiving.rdc.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum LabelAction {
  BACKOUT("LABEL_BACKOUT"),
  CORRECTION("PALLET_REMOVAL"),
  DA_BACKOUT("DA_LABEL_BACKOUT"),
  DA_CORRECTION("DA_PALLET_REMOVAL");

  private String action;
}
