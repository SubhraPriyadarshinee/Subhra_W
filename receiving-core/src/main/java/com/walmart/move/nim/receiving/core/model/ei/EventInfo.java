package com.walmart.move.nim.receiving.core.model.ei;

import java.time.ZonedDateTime;
import lombok.Data;

@Data
public class EventInfo {

  private Integer producerIdentifier;
  private ZonedDateTime eventReceivedTs;
  private ZonedDateTime eventFromCreationTs;
  private String corelationId;
  private String eventFromTimeZone;
}
