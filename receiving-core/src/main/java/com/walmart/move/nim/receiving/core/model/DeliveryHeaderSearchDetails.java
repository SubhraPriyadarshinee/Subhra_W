package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Setter
@Getter
public class DeliveryHeaderSearchDetails {
  List<String> deliveryStatusList;
  List<String> statusReasonCodes;
  List<String> upcs;
  private Integer ageThresholdInHours;
  String doorNumber;
  List<String> poNumbers;
}
