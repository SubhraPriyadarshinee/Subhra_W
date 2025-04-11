package com.walmart.move.nim.receiving.mfc.model.osdr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MFCOSDRReceipt {

  private String type;
  private Integer quantity;
  private String uom;
  private String reasonCode;
}
