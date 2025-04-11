package com.walmart.move.nim.receiving.core.client.fit;

import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode
public class ProblemCountByDeliveryResponsePO {

  private String poNumber;
  private String issueQtyUom;
  private int issueQtyWithOutPoLines;
  private int issueQtyWithPoLines;
  private List<ProblemCountByDeliveryResponsePOLine> poLines = new ArrayList<>();
}
