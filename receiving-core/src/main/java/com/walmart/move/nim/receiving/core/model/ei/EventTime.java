package com.walmart.move.nim.receiving.core.model.ei;

import lombok.Data;

@Data
public class EventTime {

  private Integer hour;
  private Integer minute;
  private Integer second;
  private Integer nano;
}
