package com.walmart.move.nim.receiving.core.model.delivery.meta;

import lombok.Data;

@Data
public class PoProgressDetails {
  Integer poReceivedPercentage;

  Integer totalPoQty;

  String qtyUOM;

  String poNumber;
}
