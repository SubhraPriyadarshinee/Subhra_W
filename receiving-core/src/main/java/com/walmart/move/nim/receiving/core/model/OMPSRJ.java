package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class OMPSRJ {
  private List<OMPSRJDataItem> Data;
}
