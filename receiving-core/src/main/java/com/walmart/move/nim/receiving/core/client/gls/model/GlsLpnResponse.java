package com.walmart.move.nim.receiving.core.client.gls.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlsLpnResponse {

  private String palletTagId;
  private String timestamp;
}
