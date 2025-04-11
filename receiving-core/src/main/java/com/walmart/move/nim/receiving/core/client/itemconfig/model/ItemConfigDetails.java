package com.walmart.move.nim.receiving.core.client.itemconfig.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class ItemConfigDetails {
  private String item;
  private String desc;
  private String createdDateTime;
}
