package com.walmart.move.nim.receiving.core.model.ei;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
public class Inventory {

  private EventInfo eventInfo;
  private InventoryQuantity quantity;
  private Integer whseAreaCode;
  private String channelType;
  private ItemIdentifier itemIdentifier;
  private String idempotentKey;
  private String trackingNumber;
  private Documents documents;
  private Nodes nodes;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Integer messageCode;
}
