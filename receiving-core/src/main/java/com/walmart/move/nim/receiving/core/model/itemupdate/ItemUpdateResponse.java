package com.walmart.move.nim.receiving.core.model.itemupdate;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ItemUpdateResponse {
  private String statusMessage;
  private String message;
  private String country;
  private String division;
  private String node;
}
