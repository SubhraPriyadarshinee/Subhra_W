package com.walmart.move.nim.receiving.rdc.model.symbotic;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class SymLabelData {
  private String labelTagType;
  private String freightType;
  private String po;
  private String poLine;
  private String poCreateDate;
  private Long itemNumber;
  private String labelDate;
  private String holdStatus;
  private Boolean isShipLabel;
  private Integer originStore;
  private ShipLabelData shipLabelData;
}
