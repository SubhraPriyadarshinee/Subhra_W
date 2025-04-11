package com.walmart.move.nim.receiving.mfc.model.controller;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Setter
@Getter
public class InvoiceNumberDetectionResponse {
  private Long deliveryNumber;
  private String trackingId;
  private String gtin;
  private Invoice invoice;
  private InvoiceMeta meta;
}
