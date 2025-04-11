package com.walmart.move.nim.receiving.core.model.gdm.v3;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class Stop {
  private List<String> commodityTypes;
  private String transLoadId;
  private String stopId;
  private String stopType;
  private String stopStatus;
  private String stopLocationTimeZone;
  private Integer stopSequenceNbr;
  private String inductWindowEndTs;
  private String actualArrivalTs;
}
