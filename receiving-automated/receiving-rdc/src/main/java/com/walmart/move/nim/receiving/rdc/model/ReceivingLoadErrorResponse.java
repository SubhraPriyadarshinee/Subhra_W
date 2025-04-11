package com.walmart.move.nim.receiving.rdc.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class ReceivingLoadErrorResponse {

  private String type;
  private String desc;
}
