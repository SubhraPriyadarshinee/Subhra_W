package com.walmart.move.nim.receiving.core.model;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class TemporaryPalletTiHiRequest {

  @NotNull
  @Min(value = 1, message = ReceivingException.TEMPORARY_PALLET_TI_ERROR)
  private Integer palletTi;

  @NotNull
  @Min(value = 1, message = ReceivingException.TEMPORARY_PALLET_HI_ERROR)
  private Integer palletHi;

  @NotNull(message = ReceivingException.VERSION_NOT_NULL)
  private Integer version;
}
