package com.walmart.move.nim.receiving.endgame.model;

import com.walmart.move.nim.receiving.core.model.gdm.v3.AddOnService;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PoLineReceipt {

  private Integer poLineNbr;
  private Long itemNbr;
  private String gtin;
  private Integer orderedQty;
  private String qtyUom;
  private List<LineStatusInfo> lineStatusInfo;
  private List<AddOnService> addonServices;
}
