package com.walmart.move.nim.receiving.core.client.nimrds.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ItemDetailsRequestBody {

  private List<String> itemNumbers;
}
