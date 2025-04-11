package com.walmart.move.nim.receiving.fixture.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PalletPutAwayResponse {
  private String packNumber;
  private String lpn;
  private String storeNumber;
  private String location;
}
