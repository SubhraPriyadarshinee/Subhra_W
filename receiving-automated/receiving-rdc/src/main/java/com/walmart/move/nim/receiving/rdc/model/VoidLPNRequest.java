package com.walmart.move.nim.receiving.rdc.model;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class VoidLPNRequest {
  private String deliveryNumber;
  private List<ReceivedQuantityByLines> receivedQuantityByLines;
}
