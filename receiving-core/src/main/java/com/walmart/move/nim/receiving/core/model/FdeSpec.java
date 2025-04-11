package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.Getter;

public class FdeSpec {
  @Getter private String fulfillmentProviderId;
  @Getter private String endPoint;
  @Getter private List<String> channelMethod;
  @Getter private List<Integer> facilityNum;
}
