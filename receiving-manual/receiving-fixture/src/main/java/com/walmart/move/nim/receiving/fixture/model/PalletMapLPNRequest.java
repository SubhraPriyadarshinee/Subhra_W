package com.walmart.move.nim.receiving.fixture.model;

import javax.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PalletMapLPNRequest {
  @NotEmpty private String lpn;
  @NotEmpty private String packNumber;
}
