package com.walmart.move.nim.receiving.rc.model.gad;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class Disposition {
  private String proposedDisposition;
  private String finalDisposition;
  private String legacySellerId;
  private String sellerName;
  private String itemCondition;
  private String dispositionType;
}
