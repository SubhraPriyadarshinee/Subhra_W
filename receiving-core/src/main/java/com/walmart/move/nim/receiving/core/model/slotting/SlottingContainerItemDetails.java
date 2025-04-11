package com.walmart.move.nim.receiving.core.model.slotting;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SlottingContainerItemDetails {
  @NotNull(message = ReceivingException.SLOTTING_VALIDATION_ERROR_ITEMNBR)
  private long itemNbr;

  private String containerTrackingId;
  private String packTypeCode;
  private String handlingMthdCode;
  private Integer qty;
  private String qtyUom;
  private Integer vnpkRatio;
  private Integer whpkRatio;
  private String location;
  private String status;
  private String stockType;

  private Integer wareHouseTi;
  private Integer wareHouseHi;
  private Float whpkWeight;
  private Double whpkHeight;
  private Double whpkLength;
  private Double whpkWidth;
  private String rotateDate;
  private String financialReportingGroup;
  private String itemUPC;
  private String baseDivisionCode;
}
