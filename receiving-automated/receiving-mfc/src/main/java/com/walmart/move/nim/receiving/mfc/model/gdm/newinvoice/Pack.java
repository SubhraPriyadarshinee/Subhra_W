package com.walmart.move.nim.receiving.mfc.model.gdm.newinvoice;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Pack {
  private String palletNumber;
  private String packNumber;
  private List<Item> items;
}
