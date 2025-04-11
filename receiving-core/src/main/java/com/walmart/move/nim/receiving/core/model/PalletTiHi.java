package com.walmart.move.nim.receiving.core.model;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PalletTiHi {

  @Min(value = 1)
  @NotNull
  private Integer palletTi;

  @Min(value = 1)
  @NotNull
  private Integer palletHi;

  @NotNull private Integer version;
}
