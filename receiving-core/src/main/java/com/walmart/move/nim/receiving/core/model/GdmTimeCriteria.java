package com.walmart.move.nim.receiving.core.model;

import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GdmTimeCriteria {
  private String type;
  private Instant from;
  private Instant to;
}
