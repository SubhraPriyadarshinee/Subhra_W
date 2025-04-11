package com.walmart.move.nim.receiving.core.model.witron;

import com.walmart.move.nim.receiving.utils.common.MessageData;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WitronPutawayMessage extends MessageData {
  private String action;
  private Long deliveryNumber;
  private String trackingId;
  private String parentTrackingId;
  private Date completeTs;
  private String containerType;
  private Float cube;
  private String cubeUOM;
  private Float weight;
  private String weightUOM;
  private String inventoryStatus;
  private String sourceLocationId;
  private List<WitronPutawayItem> contents;
}
