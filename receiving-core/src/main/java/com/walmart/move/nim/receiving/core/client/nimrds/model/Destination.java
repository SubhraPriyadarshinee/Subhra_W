package com.walmart.move.nim.receiving.core.client.nimrds.model;

import lombok.Data;

@Data
public class Destination {
  private String store;
  private String slot;
  private String zone;
  private String division;
  private Integer slot_size;
}
