package com.walmart.move.nim.receiving.core.model.inventory;

import java.util.ArrayList;
import lombok.Data;

@Data
public class SourceItem {
  public ArrayList<ItemIdentifier> itemIdentifiers;
  public int quantity;
}
