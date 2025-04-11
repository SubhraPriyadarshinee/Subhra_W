package com.walmart.move.nim.receiving.core.client.nimrds.model;

import java.util.List;
import lombok.Data;

@Data
public class Result {

  private Integer manifest;
  private Integer quantity;
  private List<StoreDistribution> storeDistrib = null;
}
