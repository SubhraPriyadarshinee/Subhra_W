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
public class PrintRequest {

  private List<Datum> data;
  private String formatName;
  private String labelIdentifier;
  private long ttlInHours;
}
