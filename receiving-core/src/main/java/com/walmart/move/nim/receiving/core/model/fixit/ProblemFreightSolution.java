package com.walmart.move.nim.receiving.core.model.fixit;

import com.walmart.move.nim.receiving.core.model.Resolution;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelData;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ProblemFreightSolution {

  private String problemTrackingId;

  private Integer problemQty;

  private String problemStatus;

  private List<Resolution> resolutions;

  private List<PurchaseOrderLine> purchaseOrderLine;

  private PrintLabelData printDataRequest;
}
