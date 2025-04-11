package com.walmart.move.nim.receiving.endgame.model;

import com.walmart.move.nim.receiving.endgame.message.common.ScanEventData;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ReceivingRequest extends ScanEventData {
  private Integer quantity;
  private String quantityUOM;
  @NotNull private Boolean isMultiSKU;
}
