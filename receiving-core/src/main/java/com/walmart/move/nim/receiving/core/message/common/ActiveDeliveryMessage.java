package com.walmart.move.nim.receiving.core.message.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveDeliveryMessage {
  private Long deliveryNumber;
  private String deliveryStatus;
  private String url;
}
