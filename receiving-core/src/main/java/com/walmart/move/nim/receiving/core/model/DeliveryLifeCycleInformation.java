package com.walmart.move.nim.receiving.core.model;

import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.util.Date;
import lombok.*;

@Getter
@Setter
@ToString
@AllArgsConstructor
public class DeliveryLifeCycleInformation {
  DeliveryStatus type;
  Date date;
  String userId;
}
