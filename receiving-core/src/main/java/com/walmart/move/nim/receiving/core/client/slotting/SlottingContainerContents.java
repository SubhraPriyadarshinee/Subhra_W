package com.walmart.move.nim.receiving.core.client.slotting;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SlottingContainerContents {

  @NotNull(message = ReceivingException.SLOTTING_VALIDATION_ERROR_ITEMNBR)
  private Long itemNbr;

  @NotBlank(message = ReceivingException.SLOTTING_VALIDATION_ERROR_GTIN)
  private String gtin;

  @NotBlank(message = ReceivingException.SLOTTING_VALIDATION_ERROR_PROFILEDWAREHOUSEAREA)
  private String profiledWarehouseArea;

  @NotBlank(message = ReceivingException.SLOTTING_VALIDATION_ERROR_GROUPCODE)
  private String groupCode;

  @NotNull(message = ReceivingException.SLOTTING_VALIDATION_ERROR_WAREHOUSEAREACODE)
  private Integer warehouseAreaCode;
}
