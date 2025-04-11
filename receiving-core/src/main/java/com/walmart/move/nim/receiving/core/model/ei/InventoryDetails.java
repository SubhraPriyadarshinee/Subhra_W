package com.walmart.move.nim.receiving.core.model.ei;

import java.util.List;
import lombok.Data;

@Data
public class InventoryDetails {

  private List<Inventory> inventory;
}
