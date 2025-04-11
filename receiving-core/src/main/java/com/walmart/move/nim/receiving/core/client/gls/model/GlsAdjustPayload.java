package com.walmart.move.nim.receiving.core.client.gls.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@ToString
public class GlsAdjustPayload {
  private String palletTagId;
  private Integer originalQty;
  private Integer newQty;
  private String qtyUOM;
  private String operationTimestamp; // : "2022-06-08T00:00:00.000Z",
  private String reasonCode; // VTR" OR "RCV-CORRECTION"
  private String createUser;
}
