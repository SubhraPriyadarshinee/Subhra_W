package com.walmart.move.nim.receiving.core.model.gdm.v3;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode
public class FinalizePOLine {

  private int lineNumber;
  private int rcvdQty;
  private String rcvdQtyUom;
  private FinalizePOOSDRInfo damage;
  private FinalizePOOSDRInfo overage;
  private FinalizePOOSDRInfo reject;
  private FinalizePOOSDRInfo shortage;
}
