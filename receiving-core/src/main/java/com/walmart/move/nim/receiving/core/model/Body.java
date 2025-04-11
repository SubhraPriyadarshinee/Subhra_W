package com.walmart.move.nim.receiving.core.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Body {
  public String poNumber;
  public String deliveryNumber;
  public String poClosureStatus;
}
