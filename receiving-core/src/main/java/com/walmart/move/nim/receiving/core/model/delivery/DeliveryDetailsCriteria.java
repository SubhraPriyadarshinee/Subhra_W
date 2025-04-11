package com.walmart.move.nim.receiving.core.model.delivery;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class DeliveryDetailsCriteria {
  List<Long> deliveryNumbers;
  int pageNumber;
  int pageSize;
}
