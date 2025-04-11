package com.walmart.move.nim.receiving.core.model.slotting;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.AUTOMATION_TYPE_DEMATIC;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.AUTOMATION_TYPE_SCHAEFER;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.AUTOMATION_TYPE_SWISSLOG;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SlottingPalletResponse {

  @NotBlank(message = ReceivingException.SLOTTING_VALIDATION_ERROR_MESSAGEID)
  private String messageId;

  @NotEmpty(message = ReceivingException.SLOTTING_VALIDATION_ERROR_RXDIVERTLOCATIONS)
  private List<SlottingDivertLocations> locations = new ArrayList();

  private String automationType;

  public boolean isMechContainer() {
    return AUTOMATION_TYPE_DEMATIC.equalsIgnoreCase(automationType)
        || AUTOMATION_TYPE_SCHAEFER.equalsIgnoreCase(automationType)
        || AUTOMATION_TYPE_SWISSLOG.equalsIgnoreCase(automationType);
  }
}
