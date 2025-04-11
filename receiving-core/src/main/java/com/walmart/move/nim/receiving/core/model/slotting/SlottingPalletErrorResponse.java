package com.walmart.move.nim.receiving.core.model.slotting;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SlottingPalletErrorResponse {

  private String type;
  private String code;
  private String desc;
}
