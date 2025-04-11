package com.walmart.move.nim.receiving.mfc.model.gdm.newinvoice;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Pallet {
  private String palletNumber;
  private String receivedOvgType;
}
