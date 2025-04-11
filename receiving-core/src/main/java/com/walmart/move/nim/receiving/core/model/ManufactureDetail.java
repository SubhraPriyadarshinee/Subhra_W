package com.walmart.move.nim.receiving.core.model;

import lombok.Data;

@Data
public class ManufactureDetail {
  private String lot;
  private String serial;
  private String expiryDate;
  private Integer qty;
  private String reportedUom;
  private String trackingStatus;
  private String gtin;
  private String sscc;
  private String documentPackId;
  private String documentId;
  private String shipmentNumber;
}
