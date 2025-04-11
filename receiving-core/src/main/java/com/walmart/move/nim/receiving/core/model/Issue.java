package com.walmart.move.nim.receiving.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Issue {
  private String id;
  private String identifier;
  private String type;
  private String subType;
  private String deliveryNumber;
  private String upc;
  private Long itemNumber;
  private Integer quantity;
  private String status;
  private String uom;
  private String businessStatus;
  private String resolutionStatus;
  private String receivingSystem;
}
