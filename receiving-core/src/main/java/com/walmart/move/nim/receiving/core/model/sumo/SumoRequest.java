package com.walmart.move.nim.receiving.core.model.sumo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** Reference : http://amp.docs.walmart.com/sumo/api-references.html */
@Getter
@Setter
@ToString
@AllArgsConstructor
public class SumoRequest {
  private SumoNotification notification;
  private SumoAudience audience;
  private String expire_ts;
}
