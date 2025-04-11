package com.walmart.move.nim.receiving.endgame.model;

import com.walmart.move.nim.receiving.core.common.LabelType;
import com.walmart.move.nim.receiving.endgame.constants.LabelGenMode;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Builder
@Getter
@ToString
public class LabelRequestVO {
  private String deliveryNumber;
  private String door;
  private String trailerId;
  private long quantity;
  private LabelType type;
  private LabelGenMode labelGenMode;
  private String carrierName;
  private String carrierScanCode;
  private String billCode;
}
