package com.walmart.move.nim.receiving.core.client.slotting;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SlottingPalletBuildRequest {

  @NotBlank(message = ReceivingException.SLOTTING_VALIDATION_ERROR_MESSAGEID)
  private String messageId;

  @NotBlank(message = ReceivingException.SLOTTING_VALIDATION_ERROR_DOORNUMBER)
  private String sourceLocation;

  @NotNull(message = ReceivingException.SLOTTING_VALIDATION_ERROR_CONTAINER)
  private SlottingContainer container;
}
