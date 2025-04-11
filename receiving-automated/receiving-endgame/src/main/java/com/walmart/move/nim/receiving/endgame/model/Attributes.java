package com.walmart.move.nim.receiving.endgame.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
public class Attributes {
  private Boolean isFHSExceeded;
  private Boolean totable;
  private Boolean isFTS;
  private Boolean isHazmat;
  private Boolean isConsumable;
  // Chem / Non-Chem or Null
  private String itemTag;
  private String itemPrep;
}
