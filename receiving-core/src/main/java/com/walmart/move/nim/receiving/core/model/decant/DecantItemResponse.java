package com.walmart.move.nim.receiving.core.model.decant;

import java.util.List;
import lombok.Data;

@Data
public class DecantItemResponse {
  private List<PayloadItem> payload;
  private List<Object> errors;
  private String status;
}
