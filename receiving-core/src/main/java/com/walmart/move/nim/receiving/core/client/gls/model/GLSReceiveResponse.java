package com.walmart.move.nim.receiving.core.client.gls.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GLSReceiveResponse {

  private String palletTagId;
  private String slotId;
  private String timestamp;
  private Double weight;
  private String weightUOM;
}
