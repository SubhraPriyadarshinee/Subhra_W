package com.walmart.move.nim.receiving.core.model.platform;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class SchedulerJsonConfig {
  private String facilityCountryCode;
  private Integer facilityNum;
}
