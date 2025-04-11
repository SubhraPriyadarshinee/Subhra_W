package com.walmart.move.nim.receiving.core.model.itemupdate;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class ItemUpdateRequest {
  private String eventType;
  private String correlationId;
  private String source;
  private String country;
  private String division;
  private String nodeType;
  private Long itemNbr;
  private Integer facilityNumber;
  private ItemUpdateContent content;
}
