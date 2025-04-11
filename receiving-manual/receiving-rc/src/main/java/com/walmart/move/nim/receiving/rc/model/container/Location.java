package com.walmart.move.nim.receiving.rc.model.container;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class Location {
  private String name;
  private Integer orgUnitId;
  private Long locationId;
}
