package com.walmart.move.nim.receiving.rc.model.gdm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnOrderLine {
  private Integer lineNumber;
  private Integer soLineNumber;
  private Integer soLineId;
  private String receivingNode;
  private String rcType;
  private String rcId;
  private ReturnInformation returnInformation;
  private CarrierInformation carrierInformation;
  private Returned returned;
}
