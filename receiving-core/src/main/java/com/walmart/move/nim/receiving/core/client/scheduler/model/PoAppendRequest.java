package com.walmart.move.nim.receiving.core.client.scheduler.model;

import java.util.List;
import lombok.*;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoAppendRequest {
  String deliveryId;
  List<ExternalPurchaseOrder> externalPurchaseOrderList;
  String source;
}
