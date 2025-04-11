package com.walmart.move.nim.receiving.core.model.ei;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
public class ItemIdentifier {

  private String value;
  private Long itemNbr;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Integer requestedItemNbr;

  private Integer puchaseCompanyId;
}
