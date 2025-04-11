package com.walmart.move.nim.receiving.fixture.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemDetails {

  private String id;
  private String description;
  private int quantity;
  private String destination;
  private String purchaseOrder;
  private String poLineNumber;
  private String promiseDate;
}
