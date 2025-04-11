package com.walmart.move.nim.receiving.mfc.model.controller;

import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InvoiceNumberDetectionRequest {

  @NotNull private String trackingId;
  @NotNull private String gtin;
  private Long deliveryNumber;
}
