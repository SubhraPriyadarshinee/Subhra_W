package com.walmart.move.nim.receiving.core.model.yms.v2;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Setter
@Getter
public class DeliveryUnloaderDetails {
  private String unLoaderId;
  private String unLoaderFirstName;
  private String unLoaderLastName;
  private String unLoaderAssignedTimestamp;
}
