package com.walmart.move.nim.receiving.core.client.nimrds.model;

import java.util.List;
import lombok.Data;

@Data
public class Found {

  private String poNumber;
  private Integer poLine;
  private List<Result> results = null;
  private Integer total;
}
