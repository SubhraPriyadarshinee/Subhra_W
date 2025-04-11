package com.walmart.move.nim.receiving.fixture.model;

import java.util.List;
import lombok.Data;

@Data
public class CTErrorsReceivingInventoryErrors {

  private String lpn;
  List<CTInventoryError> errors;
}
