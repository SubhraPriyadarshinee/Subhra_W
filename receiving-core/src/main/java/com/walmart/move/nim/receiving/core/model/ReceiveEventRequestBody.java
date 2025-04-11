package com.walmart.move.nim.receiving.core.model;

import lombok.*;

@Data
@Getter
@Builder
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReceiveEventRequestBody {
  private String eventType;
  private Long deliveryNumber;
  private String poNumber;
  private String line;
  private ReceiveData receiveData;
}
