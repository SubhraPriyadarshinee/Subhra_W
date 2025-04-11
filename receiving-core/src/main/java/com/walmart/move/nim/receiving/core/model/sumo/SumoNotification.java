package com.walmart.move.nim.receiving.core.model.sumo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Builder
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class SumoNotification {

  private String title;
  private String alert;
  private SumoCustomData customData;
  private SumoContent android;

  public SumoNotification(String title, String alert) {
    this.title = title;
    this.alert = alert;
  }
}
