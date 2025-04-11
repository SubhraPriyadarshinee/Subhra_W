package com.walmart.move.nim.receiving.endgame.model;

import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@EqualsAndHashCode
@ToString
public class DivertDestinationFromSlotting {

  private Long itemNbr;
  private String caseUPC;
  private String divertLocation;
  private String sellerId;
  private String errorMessage;
  private Map<String, String> attributes;
}
