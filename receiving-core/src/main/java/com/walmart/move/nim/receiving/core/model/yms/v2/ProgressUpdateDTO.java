package com.walmart.move.nim.receiving.core.model.yms.v2;

import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import lombok.*;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProgressUpdateDTO {
  private Long deliveryNumber;
  private DeliveryStatus deliveryStatus;
}
