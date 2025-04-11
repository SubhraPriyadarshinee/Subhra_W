package com.walmart.move.nim.receiving.utils.constants;

import lombok.Getter;

@Getter
public enum ContainerException {
  NO_ALLOCATION_FOUND("NA"),
  OVERAGE("OV"),
  CHANNEL_FLIP("CF"),
  DOCK_TAG("DT"),
  XBLOCK("XB"),
  NO_DELIVERY_DOC("ND");

  private String text;

  ContainerException(String text) {
    this.text = text;
  }
}
