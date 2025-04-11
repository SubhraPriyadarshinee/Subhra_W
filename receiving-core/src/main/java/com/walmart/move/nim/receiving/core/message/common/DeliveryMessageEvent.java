package com.walmart.move.nim.receiving.core.message.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryMessageEvent extends MessageData {

  private ShipmentData data;
  private String eventId;
  private String type;

  @JsonProperty("WMT_CorrelationId")
  private String wMTCorrelationId;
}
