package com.walmart.move.nim.receiving.core.model.itemupdate;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ItemUpdateError {
  private String code;
  private String description;
  private String info;
  private String severity;
  private String category;
}
