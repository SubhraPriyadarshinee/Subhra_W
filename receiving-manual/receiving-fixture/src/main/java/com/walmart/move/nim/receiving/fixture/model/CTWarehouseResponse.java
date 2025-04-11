package com.walmart.move.nim.receiving.fixture.model;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CTWarehouseResponse implements Serializable {
  private String status;
  private List<CTErrorsReceivingInventoryErrors> errors;
}
