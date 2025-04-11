package com.walmart.move.nim.receiving.core.model.delivery.meta;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class DeliveryPOMap {
  Map<String, List<String>> deliveryPOs;
}
