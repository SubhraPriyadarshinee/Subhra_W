package com.walmart.move.nim.receiving.witron.model;

import java.io.Serializable;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
public class GdcLabelData implements Serializable {
  private String key;
  private String value;
}
