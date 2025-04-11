package com.walmart.move.nim.receiving.core.model.slotting;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import javax.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SlottingInstructionUpdateRequest {
  @NotBlank(message = ReceivingException.SLOTTING_VALIDATION_ERROR_QTY)
  private Integer qty;
}
