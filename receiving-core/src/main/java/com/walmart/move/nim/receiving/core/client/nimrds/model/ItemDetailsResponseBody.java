package com.walmart.move.nim.receiving.core.client.nimrds.model;

import java.util.List;
import lombok.Data;

@Data
public class ItemDetailsResponseBody {

  private List<Item> found;
  private List<Error> errors;
  private List<String> missing;
}
