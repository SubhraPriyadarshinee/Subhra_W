package com.walmart.move.nim.receiving.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class LocationInfo {
  private String mappedFloorLine;
  private String mappedPbylArea;
  private String mappedParentAclLocation;
  private String mappedDecantStation;
  private Boolean isOnline;
  private Boolean isFloorLine;
  private Boolean isFlibLocation;
  private Boolean isMultiManifestLocation;
  private Boolean isFullyDaCon;
  private String locationId; // is same as location name or just name
  private String locationType;
  private String locationSubType;
  private String sccCode;
  private String locationName; // is same as location name or just name
  private Boolean isPrimeSlot;
  private String automationType;
}
