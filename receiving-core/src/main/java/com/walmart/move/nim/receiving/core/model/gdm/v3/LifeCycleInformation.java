package com.walmart.move.nim.receiving.core.model.gdm.v3;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class LifeCycleInformation {
  private String time;
  private String type;
  private String userId;
}
