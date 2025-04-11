package com.walmart.move.nim.receiving.core.common;

import java.util.UUID;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
public class OWStoreDistribution {
  private int sourceNbr;
  private int destNbr;
  private String zone;
  private int wmtItemNbr;
  private long orderDate;
  private int whpkOrderQty;
  private UUID orderTrackingNbr;
}
