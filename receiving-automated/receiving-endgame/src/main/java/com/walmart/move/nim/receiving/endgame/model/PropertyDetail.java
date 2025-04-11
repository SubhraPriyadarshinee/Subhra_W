package com.walmart.move.nim.receiving.endgame.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
public class PropertyDetail {
  private Float value;
  private String uom;

  public PropertyDetail(Float value, String uom) {
    this.value = value;
    this.uom = uom;
  }
}
