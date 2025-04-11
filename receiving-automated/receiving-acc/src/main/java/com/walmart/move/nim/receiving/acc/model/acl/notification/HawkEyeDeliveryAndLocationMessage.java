package com.walmart.move.nim.receiving.acc.model.acl.notification;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class HawkEyeDeliveryAndLocationMessage {

  private Long deliveryNumber;
  private String location;
  private String userId;
}
