package com.walmart.move.nim.receiving.core.client.gls.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class GlsLpnRequest {

  private Long deliveryNumber;
  private String poNumber;
  private Integer poLineNumber;
  private Long itemNumber;
}
