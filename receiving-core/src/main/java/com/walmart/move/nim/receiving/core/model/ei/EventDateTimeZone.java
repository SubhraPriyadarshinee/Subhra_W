package com.walmart.move.nim.receiving.core.model.ei;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventDateTimeZone {

  private Integer totalSeconds;
  private String id;
}
