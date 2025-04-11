package com.walmart.move.nim.receiving.core.model.gdm.v3;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode
public class FinalizePOOSDRInfo {

  private String claimType;
  private String code;
  private String comment;
  private Integer quantity;
  private String uom;
}
