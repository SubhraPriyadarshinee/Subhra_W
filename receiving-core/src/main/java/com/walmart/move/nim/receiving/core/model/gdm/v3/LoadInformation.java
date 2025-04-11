package com.walmart.move.nim.receiving.core.model.gdm.v3;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class LoadInformation {
  private String number;
  private TrailerInformation trailerInformation;
  private String type;
  private String sourceDCNumber;
  private String sourceType;
}
