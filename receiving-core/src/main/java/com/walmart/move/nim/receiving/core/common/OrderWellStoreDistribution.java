package com.walmart.move.nim.receiving.core.common;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
public class OrderWellStoreDistribution {
  private Integer sourceNbr;
  private Integer wmtItemNbr;
  private Integer destNbr;
  private String poNbr;
  private Integer poType;
  private Integer whpkOrderQty;
}
