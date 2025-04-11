package com.walmart.move.nim.receiving.core.model.yms.v2;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Setter
@Getter
public class DeliveryProgressUpdateHeader {

  private String eventType;
  private String version;
  private String sourceId;
  private String eventTimestamp;
  private String correlationId;
  private String userId;
  private Integer dcNumber;
  private String countryCode;
  private String facilityCountryCode;
  private Integer facilityNum;
}
