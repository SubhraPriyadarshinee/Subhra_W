package com.walmart.move.nim.receiving.rdc.model.symbotic;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ShipLabelData {
  private String dataMatrix;
  private Long item;
  private String upcBarcode;
  private String itemDesc;
  private String poevent;
  private Integer cpQty;
  private String hazmatCode;
  private String dept;
  private String storeZone;
  private String poLine;
  private String po;
  private String poCode;
}
