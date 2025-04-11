package com.walmart.move.nim.receiving.sib.model.inventory;

import java.util.List;
import lombok.Data;

@Data
public class AggrInvListItem {
  private List<ContainerInventoriesItem> containerInventories;
}
