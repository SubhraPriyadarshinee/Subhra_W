package com.walmart.move.nim.receiving.core.model.decant;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ItemInfos {
  private String baseDivisionCode;
  private String financialReportingGroupCode;
  private Integer vnpkQty;
  private Integer whpkQty;
  private Double whpkSell;
  private Integer deptNumber;
  private Integer vendorNumber;
  private Float vnpkWgtQty;
  private String vnpkWgtUom;
  private Float vnpkcbqty;
  private String vnpkcbuomcd;
  private List<String> descriptions;
  private Long itemNumber;
  private String orderableGtin;
  private String consumableGtin;
  private Integer pluNumber;
  private String cid;
  private String quantityUom;
  private Integer deptCategory;
  private String hybridStorageFlag;
  private String deptSubcatgNbr;
}
