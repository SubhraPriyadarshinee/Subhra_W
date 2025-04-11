package com.walmart.move.nim.receiving.reporting.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportDeliveryDocumentLine {

  private String purchaseReferenceNumber;

  private int purchaseReferenceLineNumber;

  private String purchaseRefType;

  private String itemUPC;

  private Long itemNbr;

  private Integer orderedLabelCount;

  private Integer overageLabelCount;

  private Integer exceptionLabelCount;
}
