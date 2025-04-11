package com.walmart.move.nim.receiving.fixture.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PutAwayInventory {
  private String palletId;
  private String lpn;
  private String destination;
  private String putAwayLocation;
  private List<ItemDetails> items;
  private int weight;
}
