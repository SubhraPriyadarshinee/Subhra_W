package com.walmart.move.nim.receiving.core.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.util.ObjectUtils;

@Getter
@Setter
@ToString
public class OsdrConfigSpecification {
  private String facilityCountryCode;
  private Integer facilityNum;
  private String uom;
  private int nosOfDay;

  @Getter(AccessLevel.NONE)
  private Long frequencyIntervalInMinutes;

  private long frequencyFactor; // 24/factor
  private Integer osdrPOBatchSize;

  public Long getFrequencyIntervalInMinutes() {
    // Default value to 1 day in minutes
    return ObjectUtils.isEmpty(frequencyIntervalInMinutes) ? 1440 : frequencyIntervalInMinutes;
  }
}
