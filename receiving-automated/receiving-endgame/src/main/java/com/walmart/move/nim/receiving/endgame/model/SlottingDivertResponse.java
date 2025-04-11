package com.walmart.move.nim.receiving.endgame.model;

import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@EqualsAndHashCode
@ToString
public class SlottingDivertResponse {

  private String messageId;
  private List<DivertDestinationFromSlotting> divertLocations = null;
}
