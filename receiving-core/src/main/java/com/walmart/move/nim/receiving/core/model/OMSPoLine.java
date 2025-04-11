package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OMSPoLine {
  private String oponbr;
  private String opolnbr;
  private String vndrnbr;
  private String vndrdptnbr;
  private String vndrseqnbr;
  private String vnpkordqty;
  private String itmnbr;
  private String vnpkcbqty;
  private String vnpkcbuomcd;
  private String vnpkqty;
  private String vnpkwtqty;
  private String vnpkwtqtyuomcd;
  private String cmmdtyid;
  private String chnlmthdcd;
  private Chnmthtxt chnmthtxt;
  private String hzmtid;
  private Poevtabbr poevtabbr;
  private String ltlind;
  private List<Object> omspolnallw;
  private List<OMSPoLineDest> omspolinedest;
  private List<Object> omspolinecharge;
}
