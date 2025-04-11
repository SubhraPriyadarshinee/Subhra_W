package com.walmart.move.nim.receiving.core.model;

import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/* Warehouse pack dimensions of item */
@Getter
@Setter
@ToString
public class WHPKDimensions implements Serializable {
  private String uom;
  private double depth;
  private double width;
  private double height;
}
