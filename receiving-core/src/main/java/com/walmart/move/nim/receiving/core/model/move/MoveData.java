package com.walmart.move.nim.receiving.core.model.move;

import com.google.gson.annotations.Expose;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import lombok.*;

@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MoveData extends MessageData {

  @Expose private String correlationID;
  @Expose private int moveQty;
  @Expose private String moveQtyUOM;
  @Expose private String moveEvent;
  @Expose private String fromLocation;
  @Expose private int priority;
  @Expose private double sequenceNbr;
  @Expose private MoveType moveType;
  @Expose private String toLocation;
  @Expose private String containerTag;
  @Expose private Integer destBUNumber;
}
