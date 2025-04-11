package com.walmart.move.nim.receiving.endgame.model;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class EndGameSlottingData {
  private Long deliveryNumber;
  private String doorNumber;
  private List<DivertDestinationToHawkeye> destinations;
}
