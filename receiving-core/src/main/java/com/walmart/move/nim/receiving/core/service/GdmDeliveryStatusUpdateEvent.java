package com.walmart.move.nim.receiving.core.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GdmDeliveryStatusUpdateEvent {
  private Long deliveryNumber;

  private String receiverUserId;
}
