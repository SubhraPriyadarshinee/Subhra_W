package com.walmart.move.nim.receiving.core.model.delivery.meta;

import java.util.List;
import lombok.Data;

@Data
public class ReceiveProgressMeta {
  Integer totalDeliveryQty;
  List<PoProgressDetails> poProgressDetailsList;
}
