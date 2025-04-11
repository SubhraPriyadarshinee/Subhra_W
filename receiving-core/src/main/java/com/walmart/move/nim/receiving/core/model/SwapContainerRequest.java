package com.walmart.move.nim.receiving.core.model;

import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SwapContainerRequest {
  @NotNull private String sourceLpn;
  @NotNull private String targetLpn;
}
