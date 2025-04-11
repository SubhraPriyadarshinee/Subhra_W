package com.walmart.move.nim.receiving.core.message.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** @author j0p00pb */
@Setter
@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FireflyEvent {

  private String assetId;
  private Long associationTimeEpoch;
  private String associationTime;
  private String assetType;
  private String eventName;
  private Integer businessUnitNumber;
  private String bannerCode;
  private String bannerDesc;
  private String eventTime;
  private Character tempComplianceInd;
}
