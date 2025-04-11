package com.walmart.move.nim.receiving.acc.model.acl.notification;

import com.walmart.move.nim.receiving.utils.common.MessageData;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DeliveryAndLocationMessage extends MessageData {

  private String deliveryNbr;
  private String location;
  private String userId;
}
