package com.walmart.move.nim.receiving.endgame.model;

import com.walmart.move.nim.receiving.core.model.inventory.ContainerTag;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class TransferReceivingRequest extends MessageData {
  private String trackingId;
  private String location;
  private List<ContainerTag> tags;
  private List<TransferPurchaseOrderDetails> contents;
}
