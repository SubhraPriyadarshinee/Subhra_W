package com.walmart.move.nim.receiving.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class ReOpenDeliveryInfo {

  private Long deliveryNumber;

  private String receiverUserId;
}
