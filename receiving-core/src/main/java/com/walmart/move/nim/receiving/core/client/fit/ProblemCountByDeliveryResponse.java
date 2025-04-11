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
public class ProblemCountByDeliveryResponse {

  private String deliveryNumber;
  private String issueQtyUom;
  private int issueQtyWithOutPo;
  private int issueQtyWithPo;
  private List<ProblemCountByDeliveryResponsePO> purchaseOrders = new ArrayList<>();
}
