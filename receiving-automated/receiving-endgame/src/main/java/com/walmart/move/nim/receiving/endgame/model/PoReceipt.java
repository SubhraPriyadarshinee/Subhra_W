package com.walmart.move.nim.receiving.endgame.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PoReceipt {
  private String eventId;
  private String eventType;
  private String eventSource;
  private String eventTime;
  private PoReceiptEventPayload eventPayload;
}
