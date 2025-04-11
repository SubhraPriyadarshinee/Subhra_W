package com.walmart.move.nim.receiving.mfc.model.problem.lq;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ChannelAttributes {
  private String destFacilityCountryCode;
  private String destFacilityType;
  private String destFacilityNum;
  private String originFacilityCountryCode;
  private String originFacilityType;
  private String originFacilityNum;
}
