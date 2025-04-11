package com.walmart.move.nim.receiving.core.model.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class ReceivePackRequest {

  private String asnNumber;
  private String packNumber;
  private String eventType;
}
