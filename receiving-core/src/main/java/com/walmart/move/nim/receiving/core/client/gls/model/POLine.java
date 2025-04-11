package com.walmart.move.nim.receiving.core.client.gls.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class POLine {

  private Integer poLineNumber;
  private Long receivedQty;
  private String receivedQtyUOM;
}
