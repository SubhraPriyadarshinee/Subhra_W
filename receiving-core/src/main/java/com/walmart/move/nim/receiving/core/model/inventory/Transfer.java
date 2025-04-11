package com.walmart.move.nim.receiving.core.model.inventory;

import lombok.Data;

@Data
public class Transfer {
  public Source source;
  public TargetContainer targetContainer;
  public ReasonData reasonData;
}
