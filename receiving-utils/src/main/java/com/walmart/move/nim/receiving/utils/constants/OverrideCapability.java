package com.walmart.move.nim.receiving.utils.constants;

import lombok.Getter;

@Getter
public enum OverrideCapability {
  EXPIRY("009_AUTH_EXPIRY"),
  HACCP("009_AUTH_HACCP"),
  OVERAGES("009_AUTH_OVERAGES");

  private String text;

  OverrideCapability(String text) {
    this.text = text;
  }
}
