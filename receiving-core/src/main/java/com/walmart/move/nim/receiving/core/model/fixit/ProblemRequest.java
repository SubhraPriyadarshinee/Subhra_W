package com.walmart.move.nim.receiving.core.model.fixit;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
public class ProblemRequest {

  private String apiInvoker;

  private String dcNumber;

  private String deliveryId;

  private String itemNumber;

  private String itemUPCNumber;

  private Integer palletHi;

  private Integer palletTi;

  private Integer printerId;

  private Integer problemQty;

  private String problemType;

  private boolean timeoutEnabled;

  private List<PurchaseOrderLine> purchaseOrderLines;

  private String receivingUserId;
}
