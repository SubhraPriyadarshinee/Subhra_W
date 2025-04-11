package com.walmart.move.nim.receiving.core.model;

import java.util.Date;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class PutawayHeader {
  private String eventType;
  private String messageId;
  private String correlationId;
  private String requestorId;
  private Date msgTimestamp;
  private Integer facilityNum;
  private String facilityCountryCode;
  private Integer version;
}
