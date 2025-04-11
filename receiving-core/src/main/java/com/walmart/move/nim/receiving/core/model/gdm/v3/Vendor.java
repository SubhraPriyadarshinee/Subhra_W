package com.walmart.move.nim.receiving.core.model.gdm.v3;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class Vendor {
  private Integer number;
  private String name;
  private Integer department;
  private boolean serialInfoEnabled;
}
