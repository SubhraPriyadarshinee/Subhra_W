package com.walmart.move.nim.receiving.core.client.slotting;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class SlottingPalletBuildErrorResponse {

  @NotEmpty(message = ReceivingException.SLOTTING_VALIDATION_ERROR_MESSAGES)
  private List<SlottingPalletBuildErrorResponseMessage> messages;
}
