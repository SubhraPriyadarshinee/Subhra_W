package com.walmart.move.nim.receiving.core.model.symbotic;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class SymNackMessage {
  private String status;
  private String reason;
}
