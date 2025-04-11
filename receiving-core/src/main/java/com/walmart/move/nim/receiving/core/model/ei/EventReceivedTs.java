package com.walmart.move.nim.receiving.core.model.ei;

import lombok.Data;

@Data
public class EventReceivedTs {

  private EventDateTime dateTime;
  private EventDateTimeOffset offset;
  private EventDateTimeZone zone;
}
