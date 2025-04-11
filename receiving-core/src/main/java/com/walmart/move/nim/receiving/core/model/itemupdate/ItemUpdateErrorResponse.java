package com.walmart.move.nim.receiving.core.model.itemupdate;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class ItemUpdateErrorResponse {
  private List<ItemUpdateError> error;
}
