package com.walmart.move.nim.receiving.acc.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoboDepalEventMessage extends MessageData {
  private String trackingId;
  private String sourceLocationId;
  private String destinationLocationId;
  private String messageTs;
  private String messageType;
  private String equipmentName;
  private String skuInd;
  private Integer priority;
  private RoboticCellAssignments roboticCellAssignments;
}
