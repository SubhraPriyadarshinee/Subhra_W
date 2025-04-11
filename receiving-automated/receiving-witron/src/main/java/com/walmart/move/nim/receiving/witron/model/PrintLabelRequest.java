package com.walmart.move.nim.receiving.witron.model;

import java.io.Serializable;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class PrintLabelRequest implements Serializable {
  private String formatName;
  private String labelIdentifier;
  private long ttlInHours;
  private List<GdcLabelData> data;
}
