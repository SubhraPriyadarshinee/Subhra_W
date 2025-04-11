package com.walmart.move.nim.receiving.core.model;

import java.util.Map;
import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ReceiveAllResponse {

  private Long deliveryNumber;
  private String purchaseReferenceNumber;
  private Integer purchaseReferenceLineNumber;
  private Long itemNbr;
  private Map<String, Object> printJob;
}
