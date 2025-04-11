package com.walmart.move.nim.receiving.core.model;

import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DcFinReconciledDate {
  private Long deliveryNum;
  private String documentNum;
  private Integer documentLineNum;
  private Date dateReceived;
  private Integer primaryQty;
  private String lineQtyUom;
  private String WMTUserId;
  private String containerId;
  private Long itemNumber;
  private String promoBuyInd;
  private String weightFormatType;
  private Float weight;
  private String weightUom;

  public DcFinReconciledDate() {}
}
