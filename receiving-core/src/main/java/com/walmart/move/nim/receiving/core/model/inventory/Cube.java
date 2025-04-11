package com.walmart.move.nim.receiving.core.model.inventory;

import lombok.Data;

@Data
public class Cube {
  public double value;
  public String uom;
  public String cubeUOM;
  public boolean valid;
}
