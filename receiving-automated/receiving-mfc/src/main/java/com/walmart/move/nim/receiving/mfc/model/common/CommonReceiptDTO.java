package com.walmart.move.nim.receiving.mfc.model.common;

import java.util.List;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@ToString
public class CommonReceiptDTO {
  private String gtin;
  private Long deliveryNumber;
  private String containerId;
  private List<Quantity> quantities;
}
