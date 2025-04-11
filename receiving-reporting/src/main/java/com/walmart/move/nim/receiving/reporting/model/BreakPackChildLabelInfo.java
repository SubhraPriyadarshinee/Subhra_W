package com.walmart.move.nim.receiving.reporting.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BreakPackChildLabelInfo {

  private String childLabel;
  private String allocatedStore;
}
