package com.walmart.move.nim.receiving.core.model.sorter;

import lombok.Data;

@Data
public class Pick {
  private String storeNbr;
  private Integer quantity;
  private Integer itemNbr;
  private String dc;
  private String reportingGroup;
  private String baseDivisionCode;
  private Integer divisionNbr;
  private Integer deptNbr;
  private Integer whpkQty;
  private String pickBatchNbr;
  private String printBatchNbr;
  private String aisleNbr;
  private String cartonTag;
}
