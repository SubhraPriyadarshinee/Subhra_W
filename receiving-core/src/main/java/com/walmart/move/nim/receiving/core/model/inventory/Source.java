package com.walmart.move.nim.receiving.core.model.inventory;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class Source {
  public ArrayList<ContainerIdentifier> ctnrIdentifiers;
  public List<SourceItem> items;
}
