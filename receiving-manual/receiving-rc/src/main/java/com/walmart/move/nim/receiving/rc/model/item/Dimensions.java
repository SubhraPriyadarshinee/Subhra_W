package com.walmart.move.nim.receiving.rc.model.item;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class Dimensions {
  private String uom;
  private double depth;
  private double width;
  private double height;
}
