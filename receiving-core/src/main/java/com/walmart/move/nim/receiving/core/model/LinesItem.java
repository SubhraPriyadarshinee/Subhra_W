package com.walmart.move.nim.receiving.core.model;

import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class LinesItem {
  private Long itemNumber;
  private String baseDivCode;
  private String lineQtyUOM;
  private Integer warehousePackEachQty;
  private String inboundChannelMethod;
  private List<DistributionsItem> distributions;
  private Integer vendorPackEachQty;
  private Object parentContainerId;
  private Integer primaryQty;
  private Date dateReceived;
  private String containerId;
  private Integer documentLineNo;
  private String financialReportGrpCode;
  private Boolean isItemVariableWeight;
  private Float secondaryQty;
  private String secondaryQtyUOM;
}
