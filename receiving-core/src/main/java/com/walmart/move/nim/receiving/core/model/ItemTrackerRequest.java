package com.walmart.move.nim.receiving.core.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ItemTrackerRequest {
  private String parentTrackingId;
  private String trackingId;
  private String gtin;
  private String reasonCode;
}
