package com.walmart.move.nim.receiving.core.model;

import com.google.gson.annotations.Expose;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Setter
@Getter
public class DeliveryCompletedDTO extends MessageData {

  private String deliveryStatus;
  private Long deliveryNumber;

  @Expose(serialize = false)
  private String userId;
}
