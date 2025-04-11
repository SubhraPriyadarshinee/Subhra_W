package com.walmart.move.nim.receiving.core.client.nimrds.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OrderLines {

  private String poNumber;
  private Integer poLine;
}
