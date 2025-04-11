package com.walmart.move.nim.receiving.core.model.label;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class LabelDataMiscInfo {
  private String location;
  private String purchaseRefType;
  private boolean isSymEligible;
  private String asrsAlignment;
  private String slotType;
  private int locationSize;
}
