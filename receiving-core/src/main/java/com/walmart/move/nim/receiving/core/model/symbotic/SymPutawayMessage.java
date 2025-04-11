package com.walmart.move.nim.receiving.core.model.symbotic;

import com.walmart.move.nim.receiving.utils.common.MessageData;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SymPutawayMessage extends MessageData {
  private String action;
  private String trackingId;
  private String shippingLabelId;
  private String labelType;
  private String freightType;
  private String inventoryStatus;
  private List<SymPutawayItem> contents;
}
