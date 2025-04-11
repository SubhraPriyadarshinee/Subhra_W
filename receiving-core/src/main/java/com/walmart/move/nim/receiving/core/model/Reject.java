package com.walmart.move.nim.receiving.core.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Reject {

  private Integer quantity;
  private String uom;
  private String code;
}
