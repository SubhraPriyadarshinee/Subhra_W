package com.walmart.move.nim.receiving.mfc.model.controller;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Setter
@Getter
public class InvoiceMeta {
  private String foundTrackingId;
  private String foundSSCC;
  private Long foundDelivery;
}
