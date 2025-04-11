package com.walmart.move.nim.receiving.core.client.slotting;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SlottingPalletBuildResponse {

  @NotBlank(message = ReceivingException.SLOTTING_VALIDATION_ERROR_CONTAINERTRACKINGID)
  private String containerTrackingId;

  @NotBlank(message = ReceivingException.SLOTTING_VALIDATION_ERROR_DIVERTLOCATION)
  private String divertLocation;
}
