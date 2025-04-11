package com.walmart.move.nim.receiving.core.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DCFinPOCloseStatusResponse {

  public Meta meta;
  public Body body;
}
