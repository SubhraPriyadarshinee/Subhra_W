package com.walmart.move.nim.receiving.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class Problem {
  private String problemTagId;
  private String deliveryNumber;
  private String issueId;
  private String resolutionId;
  private Integer resolutionQty;
  private String slotId;
  private String resolution;
  private String type;
  private Integer reportedQty;
  private Integer receivedQty;
  private String uom;
}
