package com.walmart.move.nim.receiving.core.model.delivery;

import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeliveryEventUpdateRequest {
  private DeliveryStatus deliveryStatus;
  private long deliveryNumber;
}
