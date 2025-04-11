package com.walmart.move.nim.receiving.core.client.itemconfig.model;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ItemConfigResponse {
  private int totalRecords;
  private List<ItemConfigDetails> items;
}
