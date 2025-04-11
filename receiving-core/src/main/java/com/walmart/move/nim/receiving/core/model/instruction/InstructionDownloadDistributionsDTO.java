package com.walmart.move.nim.receiving.core.model.instruction;

import lombok.Data;

@Data
public class InstructionDownloadDistributionsDTO {

  private String orderId;
  private Integer allocQty;
  private String qtyUom;
  private InstructionDownloadItemDTO item;
}
