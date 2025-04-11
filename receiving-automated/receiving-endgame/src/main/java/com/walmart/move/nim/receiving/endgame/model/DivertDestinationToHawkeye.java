package com.walmart.move.nim.receiving.endgame.model;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
public class DivertDestinationToHawkeye {
  private String caseUPC;
  private List<String> possibleUPCs;
  private String sellerId;
  private String destination;
  private Integer maxCaseQty;
  private Attributes attributes;
}
