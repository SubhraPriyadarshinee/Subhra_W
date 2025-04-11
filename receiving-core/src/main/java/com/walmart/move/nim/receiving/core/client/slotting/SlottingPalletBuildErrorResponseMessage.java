package com.walmart.move.nim.receiving.core.client.slotting;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SlottingPalletBuildErrorResponseMessage {

  @NotBlank(message = ReceivingException.SLOTTING_VALIDATION_ERROR_TYPE)
  private String type;

  @NotBlank(message = ReceivingException.SLOTTING_VALIDATION_ERROR_CODE)
  private String code;

  @NotBlank(message = ReceivingException.SLOTTING_VALIDATION_ERROR_DESC)
  private String desc;
}
