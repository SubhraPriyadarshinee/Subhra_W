package com.walmart.move.nim.receiving.acc.model.acl.label;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode
public class ACLLabelCount {

  private Long deliveryNumber;

  private Integer itemCount;

  private Integer labelsCount;

  private String location;

  private String equipmentName;
}
