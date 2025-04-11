package com.walmart.move.nim.receiving.core.client.itemconfig.model;

import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ItemConfigRequest {
  private Set<Long> data;
}
