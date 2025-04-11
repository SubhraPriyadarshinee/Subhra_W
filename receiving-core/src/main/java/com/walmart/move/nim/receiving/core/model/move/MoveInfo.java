package com.walmart.move.nim.receiving.core.model.move;

import com.walmart.move.nim.receiving.utils.common.MessageData;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MoveInfo extends MessageData {
  private String correlationID;
  private int moveQty;
  private String moveQtyUOM;
  private String moveEvent;
  private String fromLocation;
  private int priority;
  private double sequenceNbr;
  private MoveType moveType;
  private String toLocation;
  private String containerTag;
  private Integer vnpkQty;
  private Integer whpkQty;
  private List<MoveContainer> containerList;
}
