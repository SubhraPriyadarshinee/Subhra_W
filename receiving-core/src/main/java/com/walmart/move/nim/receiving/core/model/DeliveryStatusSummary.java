package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class DeliveryStatusSummary {
  int status;
  List<DeliveryLifeCycleInformation> lifeCycleInformation;
}
