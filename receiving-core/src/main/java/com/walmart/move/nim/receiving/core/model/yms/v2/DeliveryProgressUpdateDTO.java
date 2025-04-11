package com.walmart.move.nim.receiving.core.model.yms.v2;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Setter
@Getter
public class DeliveryProgressUpdateDTO {

  private DeliveryProgressUpdateHeader header;
  private DeliveryProgressUpdatePayload payload;
}
