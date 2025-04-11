package com.walmart.move.nim.receiving.core.model.decant;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class FTSPublishMessage {
  private String messageId;
  private Long itemNumber;
  private String trackingId;
  private String userId;
  private String eventType;
  private String containerCreatedDate;
  private String upc;
  private String sellerId;
  private String endTime;
}
