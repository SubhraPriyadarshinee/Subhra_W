package com.walmart.move.nim.receiving.rdc.label;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class CommonLabelDetails {
  private Integer receiver;
  private String slot;
  private Integer slotSize;
  private String labelTrackingId;
}
