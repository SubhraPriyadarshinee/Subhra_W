package com.walmart.move.nim.receiving.core.model.gdm.v3;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class Vnpk {
  private Integer quantity;
  private Float cost;
  private PropertyDetail weight;
  private PropertyDetail cube;
}
