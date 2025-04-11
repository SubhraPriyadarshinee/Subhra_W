package com.walmart.move.nim.receiving.core.model;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class TemporaryPalletTiHiResponse {

  @NotNull(message = ReceivingException.VERSION_NOT_NULL)
  private Integer version;
}
