package com.walmart.move.nim.receiving.core.client.fit;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode
public class ProblemCountByDeliveryResponsePOLine {

  private int poLineNumber;
  private String issueQtyUom;
  private int issueQty;
}
