package com.walmart.move.nim.receiving.mfc.model.problem.lq;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class Container {
  private List<Pallet> pallets;
  private List<Item> items;
}
